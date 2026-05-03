#include "llama.h"

#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <memory>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

#define SCOUTY_LLAMA_TAG "ScoutyLlamaCpp"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, SCOUTY_LLAMA_TAG, __VA_ARGS__)

namespace {

struct JniString {
    JNIEnv * env;
    jstring value;
    const char * chars;

    JniString(JNIEnv * env, jstring value) : env(env), value(value), chars(nullptr) {
        if (value != nullptr) {
            chars = env->GetStringUTFChars(value, nullptr);
        }
    }

    ~JniString() {
        if (value != nullptr && chars != nullptr) {
            env->ReleaseStringUTFChars(value, chars);
        }
    }

    std::string str() const {
        return chars == nullptr ? std::string() : std::string(chars);
    }
};

void throwIllegalState(JNIEnv * env, const std::string & message) {
    LOGE("%s", message.c_str());
    env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), message.c_str());
}

std::vector<llama_token> tokenize(
    const llama_vocab * vocab,
    const std::string & text,
    bool addSpecial
) {
    int32_t nTokens = llama_tokenize(
        vocab,
        text.c_str(),
        static_cast<int32_t>(text.size()),
        nullptr,
        0,
        addSpecial,
        true
    );
    if (nTokens == INT32_MIN) {
        throw std::runtime_error("llama_tokenize overflowed");
    }
    if (nTokens < 0) {
        nTokens = -nTokens;
    }

    std::vector<llama_token> tokens(static_cast<size_t>(nTokens));
    const int32_t written = llama_tokenize(
        vocab,
        text.c_str(),
        static_cast<int32_t>(text.size()),
        tokens.data(),
        nTokens,
        addSpecial,
        true
    );
    if (written < 0) {
        throw std::runtime_error("llama_tokenize failed");
    }
    tokens.resize(static_cast<size_t>(written));
    return tokens;
}

std::string tokenToPiece(const llama_vocab * vocab, llama_token token) {
    std::vector<char> buffer(128);
    int32_t written = llama_token_to_piece(
        vocab,
        token,
        buffer.data(),
        static_cast<int32_t>(buffer.size()),
        0,
        false
    );
    if (written < 0) {
        buffer.resize(static_cast<size_t>(-written));
        written = llama_token_to_piece(
            vocab,
            token,
            buffer.data(),
            static_cast<int32_t>(buffer.size()),
            0,
            false
        );
    }
    if (written < 0) {
        throw std::runtime_error("llama_token_to_piece failed");
    }
    return std::string(buffer.data(), static_cast<size_t>(written));
}

bool startsWithTokens(
    const std::vector<llama_token> & tokens,
    const std::vector<llama_token> & prefix
) {
    return tokens.size() >= prefix.size() &&
           std::equal(prefix.begin(), prefix.end(), tokens.begin());
}

std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> buildSampler(
    const llama_vocab * vocab,
    const std::string & grammar,
    int32_t topK,
    float topP,
    float temperature,
    uint32_t seed
) {
    auto chainParams = llama_sampler_chain_default_params();
    chainParams.no_perf = true;
    std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler(
        llama_sampler_chain_init(chainParams),
        llama_sampler_free
    );
    if (!sampler) {
        throw std::runtime_error("llama_sampler_chain_init failed");
    }

    if (!grammar.empty()) {
        llama_sampler_chain_add(
            sampler.get(),
            llama_sampler_init_grammar(vocab, grammar.c_str(), "root")
        );
    }

    if (temperature <= 0.0f) {
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_greedy());
    } else {
        if (topK > 0) {
            llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_k(topK));
        }
        if (topP > 0.0f && topP < 1.0f) {
            llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_p(topP, 1));
        }
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_temp(temperature));
        llama_sampler_chain_add(sampler.get(), llama_sampler_init_dist(seed));
    }

    return sampler;
}

class ScoutyLlamaContext {
  public:
    ScoutyLlamaContext(
        const std::string & modelPath,
        int32_t contextTokens,
        int32_t batchTokens,
        int32_t threads,
        bool useMmap,
        bool useMlock
    ) : model(nullptr), ctx(nullptr), vocab(nullptr), batchTokens(std::max(1, batchTokens)) {
        ggml_backend_load_all();

        llama_model_params modelParams = llama_model_default_params();
        modelParams.use_mmap = useMmap;
        modelParams.use_mlock = useMlock;
        modelParams.n_gpu_layers = 0;

        model = llama_model_load_from_file(modelPath.c_str(), modelParams);
        if (model == nullptr) {
            throw std::runtime_error("failed to load GGUF model: " + modelPath);
        }

        llama_context_params ctxParams = llama_context_default_params();
        ctxParams.n_ctx = static_cast<uint32_t>(std::max(512, contextTokens));
        ctxParams.n_batch = static_cast<uint32_t>(std::max(1, batchTokens));
        ctxParams.n_ubatch = static_cast<uint32_t>(std::max(1, std::min(batchTokens, 512)));
        ctxParams.n_threads = std::max(1, threads);
        ctxParams.n_threads_batch = std::max(1, threads);
        ctxParams.no_perf = true;

        ctx = llama_init_from_model(model, ctxParams);
        if (ctx == nullptr) {
            throw std::runtime_error("llama_init_from_model failed");
        }
        vocab = llama_model_get_vocab(model);
        if (vocab == nullptr) {
            throw std::runtime_error("llama_model_get_vocab failed");
        }
    }

    ~ScoutyLlamaContext() {
        if (ctx != nullptr) {
            llama_free(ctx);
        }
        if (model != nullptr) {
            llama_model_free(model);
        }
    }

    std::string generate(
        const std::string & prompt,
        const std::string & grammar,
        int32_t maxTokens,
        float temperature,
        int32_t topK,
        float topP,
        uint32_t seed,
        const std::string & cacheKey,
        const std::string & cachePrefix
    ) {
        std::vector<llama_token> promptTokens = tokenize(vocab, prompt, true);
        int32_t nPast = preparePromptCache(prompt, promptTokens, cacheKey, cachePrefix);
        std::vector<llama_token> tokensToEval(promptTokens.begin() + nPast, promptTokens.end());
        if (!tokensToEval.empty()) {
            decodeTokens(tokensToEval);
        }

        auto sampler = buildSampler(
            vocab,
            grammar,
            topK,
            topP,
            temperature,
            seed
        );

        std::string output;
        llama_token token = LLAMA_TOKEN_NULL;
        int32_t generated = 0;
        const int32_t limit = std::max(1, maxTokens);

        while (generated < limit) {
            token = llama_sampler_sample(sampler.get(), ctx, -1);
            if (llama_vocab_is_eog(vocab, token)) {
                break;
            }
            llama_sampler_accept(sampler.get(), token);
            output += tokenToPiece(vocab, token);

            llama_batch batch = llama_batch_get_one(&token, 1);
            if (llama_decode(ctx, batch) != 0) {
                throw std::runtime_error("llama_decode failed during generation");
            }
            generated += 1;
        }

        return output;
    }

  private:
    llama_model * model;
    llama_context * ctx;
    const llama_vocab * vocab;
    int32_t batchTokens;
    std::string cachedKey;
    int32_t cachedPrefixTokenCount = 0;

    int32_t preparePromptCache(
        const std::string & prompt,
        const std::vector<llama_token> & promptTokens,
        const std::string & cacheKey,
        const std::string & cachePrefix
    ) {
        if (cacheKey.empty() || cachePrefix.empty()) {
            llama_memory_clear(llama_get_memory(ctx), true);
            cachedKey.clear();
            cachedPrefixTokenCount = 0;
            return 0;
        }

        const bool canReuse = cachedKey == cacheKey && cachedPrefixTokenCount > 0;
        if (canReuse) {
            llama_memory_seq_rm(llama_get_memory(ctx), 0, cachedPrefixTokenCount, -1);
            return cachedPrefixTokenCount;
        }

        std::vector<llama_token> prefixTokens = tokenize(vocab, cachePrefix, true);
        if (!startsWithTokens(promptTokens, prefixTokens)) {
            llama_memory_clear(llama_get_memory(ctx), true);
            cachedKey.clear();
            cachedPrefixTokenCount = 0;
            return 0;
        }

        llama_memory_clear(llama_get_memory(ctx), true);
        decodeTokens(prefixTokens);
        cachedKey = cacheKey;
        cachedPrefixTokenCount = static_cast<int32_t>(prefixTokens.size());
        return cachedPrefixTokenCount;
    }

    void decodeTokens(const std::vector<llama_token> & tokens) {
        size_t offset = 0;
        while (offset < tokens.size()) {
            const size_t remaining = tokens.size() - offset;
            const size_t count = std::min(remaining, static_cast<size_t>(batchTokens));
            llama_batch batch = llama_batch_get_one(
                const_cast<llama_token *>(tokens.data() + offset),
                static_cast<int32_t>(count)
            );
            if (llama_decode(ctx, batch) != 0) {
                throw std::runtime_error("llama_decode failed during prompt processing");
            }
            offset += count;
        }
    }
};

ScoutyLlamaContext * fromHandle(jlong handle) {
    if (handle == 0) {
        throw std::runtime_error("native llama.cpp handle is null");
    }
    return reinterpret_cast<ScoutyLlamaContext *>(handle);
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_scouty_app_assistant_domain_runtime_LlamaCppNativeBridge_loadModel(
    JNIEnv * env,
    jobject,
    jstring modelPath,
    jint contextTokens,
    jint batchTokens,
    jint threads,
    jboolean useMmap,
    jboolean useMlock
) {
    try {
        JniString path(env, modelPath);
        auto * context = new ScoutyLlamaContext(
            path.str(),
            contextTokens,
            batchTokens,
            threads,
            useMmap == JNI_TRUE,
            useMlock == JNI_TRUE
        );
        return reinterpret_cast<jlong>(context);
    } catch (const std::exception & error) {
        throwIllegalState(env, error.what());
        return 0;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_scouty_app_assistant_domain_runtime_LlamaCppNativeBridge_generate(
    JNIEnv * env,
    jobject,
    jlong handle,
    jstring prompt,
    jstring grammar,
    jint maxTokens,
    jfloat temperature,
    jint topK,
    jfloat topP,
    jint randomSeed,
    jstring promptCacheKey,
    jstring promptCachePrefix
) {
    try {
        JniString promptString(env, prompt);
        JniString grammarString(env, grammar);
        JniString cacheKeyString(env, promptCacheKey);
        JniString cachePrefixString(env, promptCachePrefix);

        const std::string result = fromHandle(handle)->generate(
            promptString.str(),
            grammarString.str(),
            maxTokens,
            temperature,
            topK,
            topP,
            static_cast<uint32_t>(randomSeed),
            cacheKeyString.str(),
            cachePrefixString.str()
        );
        return env->NewStringUTF(result.c_str());
    } catch (const std::exception & error) {
        throwIllegalState(env, error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_scouty_app_assistant_domain_runtime_LlamaCppNativeBridge_release(
    JNIEnv * env,
    jobject,
    jlong handle
) {
    try {
        delete fromHandle(handle);
    } catch (const std::exception & error) {
        throwIllegalState(env, error.what());
    }
}
