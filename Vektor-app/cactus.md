Skip to content
cactus-compute
cactus
Repository navigation
Code
Issues
16
 (16)
Pull requests
26
 (26)
Agents
Discussions
Actions
Projects
Security and quality
Insights
Owner avatar
cactus
Public
cactus-compute/cactus
Go to file
t
T
Name		
ParkiratS
ParkiratS
LFM NPU Fallback + Tokenizer Fixes (#605)
09d3d79
 · 
yesterday
.github
mathjax
2 days ago
android
v1.14
5 days ago
apple
v1.14
5 days ago
assets
Versioned docs with quickstart and SDK chooser (#522)
last month
blog
updated llm.txt
2 days ago
cactus
LFM NPU Fallback + Tokenizer Fixes (#605)
yesterday
docs
v1.14
5 days ago
flutter
v1.14
5 days ago
libs
Curl prepack (#358)
2 months ago
python
v1.14
5 days ago
rust
v1.14
5 days ago
tests
Gemma4 multimodal multi-turn fixes (#598)
2 days ago
.gitignore
Tinyllama (#536)
3 weeks ago
.gitmodules
Basic addition of int4 functionality (#343)
2 months ago
CACTUS_VERSION
v1.14
5 days ago
CONTRIBUTING.md
Docs fixes (#524)
last month
DCO.md
Add Developer Certificate of Origin (DCO) setup and guidelines; imple…
6 months ago
LICENSE
Relax license for the community
7 months ago
README.md
v1.14
5 days ago
SECURITY.md
feat(docs): add comprehensive documentation for contributing, securit…
2 months ago
llms.txt
updated llm.txt
2 days ago
mkdocs.yml
updates
2 days ago
models.json
done (#567)
2 weeks ago
setup
Vad (#353)
2 months ago
Repository files navigation
README
Contributing
License
Security
Cactus
Logo

Docs Website GitHub HuggingFace Reddit Blog

A low-latency AI engine for mobile devices & wearables. Main features:

Fast: fastest inference on ARM CPU
Low RAM: zero-copy memory mapping ensures 10x lower RAM use than other engines
Multimodal: one SDK for speech, vision, and language models
Cloud fallback: automatically route requests to cloud models if needed
Energy-efficient: NPU-accelerated prefill
┌─────────────────┐
│  Cactus Engine  │ ←── OpenAI-compatible APIs for all major languages
└─────────────────┘     Chat, vision, STT, RAG, tool call, cloud handoff
         │
┌─────────────────┐
│  Cactus Graph   │ ←── Zero-copy computation graph (PyTorch for mobile)
└─────────────────┘     Custom models, optimised for RAM & quantisation
         │
┌─────────────────┐
│ Cactus Kernels  │ ←── ARM SIMD kernels (Apple, Snapdragon, Exynos, etc)
└─────────────────┘     Custom attention, KV-cache quant, chunked prefill
Quick Demo (Mac)
Step 1: brew install cactus-compute/cactus/cactus
Step 2: cactus transcribe or cactus run
Cactus Engine
#include "cactus.h"

cactus_model_t model = cactus_init(
    "path/to/weight/folder",
    "path to txt or dir of txts for auto-rag",
    false
);

const char* messages = R"([
    {"role": "system", "content": "You are a helpful assistant."},
    {"role": "user", "content": "My name is Henry Ndubuaku"}
])";

const char* options = R"({
    "max_tokens": 50,
    "stop_sequences": ["<|im_end|>"]
})";

char response[4096];
int result = cactus_complete(
    model,            // model handle
    messages,         // JSON chat messages
    response,         // response buffer
    sizeof(response), // buffer size
    options,          // generation options
    nullptr,          // tools JSON
    nullptr,          // streaming callback
    nullptr,          // user data
    nullptr,          // pcm audio buffer
    0                 // pcm buffer size
);
Example response from Gemma3-270m

{
    "success": true,        // generation succeeded
    "error": null,          // error details if failed
    "cloud_handoff": false, // true if cloud model used
    "response": "Hi there!",
    "function_calls": [],   // parsed tool calls
    "confidence": 0.8193,   // model confidence
    "time_to_first_token_ms": 45.23,
    "total_time_ms": 163.67,
    "prefill_tps": 1621.89,
    "decode_tps": 168.42,
    "ram_usage_mb": 245.67,
    "prefill_tokens": 28,
    "decode_tokens": 50,
    "total_tokens": 78
}
Cactus Graph
#include "cactus.h"

CactusGraph graph;
auto a = graph.input({2, 3}, Precision::FP16);
auto b = graph.input({3, 4}, Precision::INT8);

auto x1 = graph.matmul(a, b, false);
auto x2 = graph.transpose(x1);
auto result = graph.matmul(b, x2, true);

float a_data[6] = {1.1f, 2.3f, 3.4f, 4.2f, 5.7f, 6.8f};
float b_data[12] = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};

graph.set_input(a, a_data, Precision::FP16);
graph.set_input(b, b_data, Precision::INT8);

graph.execute();
void* output_data = graph.get_output(result);

graph.hard_reset(); 
API & SDK References
Reference	Language	Description
Engine API	C	Chat completion, streaming, tool calling, transcription, embeddings, RAG, vision, VAD, vector index, cloud handoff
Graph API	C++	Tensor operations, matrix multiplication, attention, normalization, activation functions
Python SDK	Python	Mac, Linux
Swift SDK	Swift	iOS, macOS, tvOS, watchOS, Android
Kotlin SDK	Kotlin	Android, iOS (via KMP)
Flutter SDK	Dart	iOS, macOS, Android
Rust SDK	Rust	Mac, Linux
React Native	JavaScript	iOS, Android
Model weights: Pre-converted weights for all supported models at huggingface.co/Cactus-Compute.

Benchmarks (CPU-only, no GPU)
All weights INT4 quantised
LFM: 1k-prefill / 100-decode, values are prefill tps / decode tps
LFM-VL: 256px input, values are latency / decode tps
Parakeet: 20s audio input, values are latency / decode tps
Missing latency = no NPU support yet
Device	LFM 1.2B	LFMVL 1.6B	Parakeet 1.1B	RAM
Mac M4 Pro	582/100	0.2s/98	0.1s/900k+	76MB
iPad/Mac M3	350/60	0.3s/69	0.3s/800k+	70MB
iPhone 17 Pro	327/48	0.3s/48	0.3s/300k+	108MB
iPhone 13 Mini	148/34	0.3s/35	0.7s/90k+	1GB
Galaxy S25 Ultra	255/37	-/34	-/250k+	1.5GB
Pixel 6a	70/15	-/15	-/17k+	1GB
Galaxy A17 5G	32/10	-/11	-/40k+	727MB
CMF Phone 2 Pro	-	-	-	-
Raspberry Pi 5	69/11	13.3s/11	4.5s/180k+	869MB
Supported Transcription Model
STT: 20s audio input on Macbook Air M3 chip
Benchmark dataset: internal evals with production users
Model	Params	End2End ms	Latency ms	Decode toks/sec	NPU	RTF	WER
UsefulSensors/moonshine-base	61M	361.35	182	262	yes	0.0180	0.1395
openai/whisper-tiny	39M	232.03	137.38	581	yes	0.0116	0.1860
openai/whisper-base	74M	329.37	178.65	358	yes	0.0164	0.1628
openai/whisper-small	244M	856.79	332.63	108	yes	0.0428	0.0930
openai/whisper-medium	769M	2085.87	923.33	49	yes	0.1041	0.0930
openai/whisper-large-v3	1.55B	5994	2050	15.72	no	0.2992	-
nvidia/parakeet-ctc-0.6b	600M	201.77	201.44	5214285	yes	0.0101	0.0930
nvidia/parakeet-tdt-0.6b-v3	600M	718.91	718.82	3583333	yes	0.0359	0.0465
nvidia/parakeet-ctc-1.1b	1.1B	279.03	278.92	4562500	yes	0.0139	0.1628
snakers4/silero-vad	-	-	-	-	-	-	-
pyannote/segmentation-3.0	-	-	-	-	-	-	-
pyannote/wespeaker-voxceleb-resnet34-LM	-	-	-	-	-	-	-
Supported LLMs
Gemma weights are often gated on HuggingFace, needs tokens
Run huggingface-cli login and input your huggingface token
Model	Features
google/gemma-3-270m-it	completion
google/functiongemma-270m-it	tools
google/gemma-3-1b-it	completion, gated
google/gemma-4-E2B-it	completion, tools, embed, vision, speech
google/gemma-3n-E2B-it	completion, tools
google/gemma-4-E4B-it	completion, tools, embed, vision, speech
google/gemma-3n-E4B-it	completion, tools
google/gemma-4-E2B-it	vision, audio, completion, tools, Apple NPU
google/gemma-4-E4B-it	vision, audio, completion, tools, Apple NPU
Qwen/Qwen3-0.6B	completion, tools, embed
Qwen/Qwen3-Embedding-0.6B	embed
Qwen/Qwen3.5-0.8B	vision, completion, tools, embed
Qwen/Qwen3-1.7B	completion, tools, embed
Qwen/Qwen3.5-2B	vision, completion, tools, embed
LiquidAI/LFM2.5-350M	completion, tools, embed
LiquidAI/LFM2-700M	completion, tools, embed
LiquidAI/LFM2-8B-A1B	completion, tools, embed
LiquidAI/LFM2.5-1.2B-Thinking	completion, tools, embed
LiquidAI/LFM2.5-1.2B-Instruct	completion, tools, embed
LiquidAI/LFM2-2.6B	completion, tools, embed
LiquidAI/LFM2-VL-450M	vision, txt & img embed, Apple NPU
LiquidAI/LFM2.5-VL-450M	vision, txt & img embed, Apple NPU
LiquidAI/LFM2.5-VL-1.6B	vision, txt & img embed, Apple NPU
tencent/Youtu-LLM-2B	completion, tools, embed
nomic-ai/nomic-embed-text-v2-moe	embed
Roadmap
Date	Status	Milestone
Sep 2025	Done	Released v1
Oct 2025	Done	Chunked prefill, KVCache Quant (2x prefill)
Nov 2025	Done	Cactus Attention (10 & 1k prefill = same decode)
Dec 2025	Done	Team grows to +6 Research Engineers
Jan 2026	Done	Apple NPU/RAM, 5-11x faster iOS/Mac
Feb 2026	Done	Hybrid inference, INT4, lossless Quant (1.5x)
Mar 2026	Coming	Qualcomm/Google NPUs, 5-11x faster Android
Apr 2026	Coming	Mediatek/Exynos NPUs, Cactus@ICLR
May 2026	Coming	Kernel→C++, Graph/Engine→Rust, Mac GPU & VR
Jun 2026	Coming	Torch/JAX model transpilers
Jul 2026	Coming	Wearables optimisations, Cactus@ICML
Aug 2026	Coming	Orchestration
Sep 2026	Coming	Full Cactus paper, chip manufacturer partners
Using this repo
┌──────────────────────────────────────────────────────────────────────────────┐
│                                                                              │
│ Step 0: if on Linux (Ubuntu/Debian)                                          │
│ sudo apt-get install python3 python3-venv python3-pip cmake                  │
│   build-essential libcurl4-openssl-dev                                       │
│                                                                              │
│ Step 1: clone and setup                                                      │
│ git clone https://github.com/cactus-compute/cactus && cd cactus              │
│ source ./setup                                                               │
│                                                                              │
│ Step 2: use the commands                                                     │
│──────────────────────────────────────────────────────────────────────────────│
│                                                                              │
│  cactus auth                         manage Cloud API key                    │
│    --status                          show key status                         │
│    --clear                           remove saved key                        │
│                                                                              │
│  cactus run <model>                  opens playground (auto downloads)       │
│    --precision INT4|INT8|FP16        quantization (default: INT4)            │
│    --token <token>                   HF token (gated models)                 │
│    --reconvert                       force reconversion from source          │
│                                                                              │
│  cactus transcribe [model]           live mic transcription (parakeet-tdt-0.6b-v3) │
│    --file <audio.wav>                transcribe file instead of mic          │
│    --precision INT4|INT8|FP16        quantization (default: INT4)            │
│    --token <token>                   HF token (gated models)                 │
│    --reconvert                       force reconversion from source          │
│                                                                              │
│  cactus download <model>             downloads model to ./weights            │
│    --precision INT4|INT8|FP16        quantization (default: INT4)            │
│    --token <token>                   HuggingFace API token                   │
│    --reconvert                       force reconversion from source          │
│                                                                              │
│  cactus convert <model> [dir]        convert model, supports LoRA merge      │
│    --precision INT4|INT8|FP16        quantization (default: INT4)            │
│    --lora <path>                     LoRA adapter to merge                   │
│    --token <token>                   HuggingFace API token                   │
│                                                                              │
│  cactus build                        build for ARM → build/libcactus.a       │
│    --apple                           Apple (iOS/macOS)                       │
│    --android                         Android                                 │
│    --flutter                         Flutter (all platforms)                 │
│    --python                          shared lib for Python FFI               │
│                                                                              │
│  cactus test                         run unit tests and benchmarks           │
│    --model <model>                   default: LFM2-VL-450M                   │
│    --transcribe_model <model>        default: moonshine-base                 │
│    --benchmark                       use larger models                       │
│    --precision INT4|INT8|FP16        regenerate weights with precision       │
│    --reconvert                       force reconversion from source          │
│    --no-rebuild                      skip building library                   │
│    --llm / --stt / --performance     run specific test suite                 │
│    --ios                             run on connected iPhone                 │
│    --android                         run on connected Android                │
│                                                                              │
│  cactus clean                        remove all build artifacts              │
│  cactus --help                       show all commands and flags             │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
Maintaining Organisations
Cactus Compute, Inc. (YC S25)
UCLA's BruinAI
Char (YC S25)
Yale's AI Society
National University of Singapore's AI Society
UC Irvine's AI@UCI
Imperial College's AI Society
University of Pennsylvania's AI@Penn
University of Michigan Ann-Arbor MSAIL
University of Colorado Boulder's AI Club
Citation
If you use Cactus in your research, please cite it as follows:

@software{cactus,
  title        = {Cactus: AI Inference Engine for Phones & Wearables},
  author       = {Ndubuaku, Henry and Cactus Team},
  url          = {https://github.com/cactus-compute/cactus},
  year         = {2025}
}
N/B: Scroll all the way up and click the shields link for resources!

About
Low-latency AI engine for mobile devices & wearables

cactuscompute.com
Topics
android ios arm mobile framework ai speech edge transformer smartphone whisper rag edge-ai on-device-ai mobile-inference quantiz llm llms llamacpp llm-inference
Resources
 Readme
License
 View license
Contributing
 Contributing
Security policy
 Security policy
 Activity
 Custom properties
Stars
 4.7k stars
Watchers
 40 watching
Forks
 365 forks
Report repository
Releases 17
v1.14
Latest
5 days ago
+ 16 releases
Packages
No packages published
Contributors
54
@HenryNdubuaku
@rshemet
@devabhixda
@jakmro
@github-actions[bot]
@ncylich
@justinl66
@kar-m
@ParkiratS
@yujonglee
@ffreality
@Copilot
@ammesatyajit
@YoungHypo
+ 40 contributors
Languages
C
46.9%
 
C++
42.6%
 
Python
5.5%
 
Shell
1.0%
 
Objective-C++
0.9%
 
Makefile
0.7%
 
Other
2.4%
Footer
© 2026 GitHub, Inc.
Footer navigation
Terms
Privacy
Security
Status
Community
Docs
Contact
Manage cookies
Do not share my personal information





Cactus for Android & Kotlin Multiplatform¶
Run AI models on-device with a simple Kotlin API.

Model weights: Pre-converted weights for all supported models at huggingface.co/Cactus-Compute.

Building¶

git clone https://github.com/cactus-compute/cactus && cd cactus && source ./setup
cactus build --android
Build output: android/libcactus.so (and android/libcactus.a)

see the main README.md for how to use CLI & download weight

Vendored libcurl (device builds)¶
To bundle libcurl locally for Android device testing, place artifacts using:

libs/curl/android/arm64-v8a/libcurl.a and libs/curl/include/curl/*.h

The build auto-detects libs/curl. You can override with:


CACTUS_CURL_ROOT=/absolute/path/to/curl cactus build --android
Integration¶
Android-only¶
Copy libcactus.so to app/src/main/jniLibs/arm64-v8a/
Copy Cactus.kt to app/src/main/java/com/cactus/
Kotlin Multiplatform¶
Source files:

File	Copy to
Cactus.common.kt	shared/src/commonMain/kotlin/com/cactus/
Cactus.android.kt	shared/src/androidMain/kotlin/com/cactus/
Cactus.ios.kt	shared/src/iosMain/kotlin/com/cactus/
cactus.def	shared/src/nativeInterop/cinterop/
Binary files:

Platform	Location
Android	libcactus.so → app/src/main/jniLibs/arm64-v8a/
iOS	libcactus-device.a → link via cinterop
build.gradle.kts:


kotlin {
    androidTarget()

    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.compilations.getByName("main") {
            cinterops {
                create("cactus") {
                    defFile("src/nativeInterop/cinterop/cactus.def")
                    includeDirs("/path/to/cactus/ffi")
                }
            }
        }
        it.binaries.framework {
            linkerOpts("-L/path/to/apple", "-lcactus-device")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
        }
    }
}
Usage¶
Handles are plain Long values (C pointers). All functions are top-level.

Basic Completion¶

import com.cactus.*

val model = cactusInit("/path/to/model", null, false)
val messages = """[{"role":"user","content":"What is the capital of France?"}]"""
val resultJson = cactusComplete(model, messages, null, null, null)
println(resultJson)
cactusDestroy(model)
For vision models (LFM2-VL, LFM2.5-VL, Gemma4, Qwen3.5), add "images": ["path/to/image.png"] to any message. For audio models (Gemma4), add "audio": ["path/to/audio.wav"]. See Engine API for details.

Completion with Options and Streaming¶

import com.cactus.*

val options = """{"max_tokens":256,"temperature":0.7}"""

val resultJson = cactusComplete(model, messages, options, null) { token, _ ->
    print(token)
}
println(resultJson)
Prefill¶
Pre-processes input text and populates the KV cache without generating output tokens. This reduces latency for subsequent calls to cactusComplete.


fun cactusPrefill(
    model: Long,
    messagesJson: String,
    optionsJson: String?,
    toolsJson: String?
): String

val tools = """[
    {
        "type": "function",
        "function": {
            "name": "get_weather",
            "description": "Get weather for a location",
            "parameters": {
                "type": "object",
                "properties": {
                    "location": {"type": "string", "description": "City, State, Country"}
                },
                "required": ["location"]
            }
        }
    }
]"""

val messages = """[
    {"role": "system", "content": "You are a helpful assistant."},
    {"role": "user", "content": "What is the weather in Paris?"},
    {"role": "assistant", "content": "<|tool_call_start|>get_weather(location=\"Paris\")<|tool_call_end|>"},
    {"role": "tool", "content": "{\"name\": \"get_weather\", \"content\": \"Sunny, 72°F\"}"},
    {"role": "assistant", "content": "It's sunny and 72°F in Paris!"}
]"""

val resultJson = cactusPrefill(model, messages, null, tools)

val completionMessages = """[
    {"role": "system", "content": "You are a helpful assistant."},
    {"role": "user", "content": "What is the weather in Paris?"},
    {"role": "assistant", "content": "<|tool_call_start|>get_weather(location=\"Paris\")<|tool_call_end|>"},
    {"role": "tool", "content": "{\"name\": \"get_weather\", \"content\": \"Sunny, 72°F\"}"},
    {"role": "assistant", "content": "It's sunny and 72°F in Paris!"},
    {"role": "user", "content": "What about SF?"}
]"""

val completion = cactusComplete(model, completionMessages, null, tools, null)
Response format:


{
    "success": true,
    "error": null,
    "prefill_tokens": 25,
    "prefill_tps": 166.1,
    "total_time_ms": 150.5,
    "ram_usage_mb": 245.67
}
Audio Transcription¶

import com.cactus.*

// From file
val resultJson = cactusTranscribe(model, "/path/to/audio.wav", null, null, null, null)
println(resultJson)

// From PCM data (16 kHz mono)
val pcmData: ByteArray = ...
val resultJson2 = cactusTranscribe(model, null, null, null, null, pcmData)
println(resultJson2)
segments contains timestamps (seconds): phrase-level for Whisper, word-level for Parakeet TDT, one segment per transcription window for Parakeet CTC and Moonshine (consecutive VAD speech regions up to 30s).


import org.json.JSONObject

val result = JSONObject(resultJson)
val segments = result.getJSONArray("segments")
for (i in 0 until segments.length()) {
    val seg = segments.getJSONObject(i)
    println("[${seg.getDouble("start")}s - ${seg.getDouble("end")}s] ${seg.getString("text")}")
}
Custom vocabulary biases the decoder toward domain-specific words (supported for Whisper and Moonshine models). Pass custom_vocabulary and vocabulary_boost in the options JSON:


val options = """{"custom_vocabulary": ["Omeprazole", "HIPAA", "Cactus"], "vocabulary_boost": 3.0}"""
val result = cactusTranscribe(model, "/path/to/audio.wav", "", options, null, null)
Streaming Transcription¶

val stream = cactusStreamTranscribeStart(model, null)
val partial = cactusStreamTranscribeProcess(stream, audioChunk)
val final_  = cactusStreamTranscribeStop(stream)
Streaming also accepts custom_vocabulary in the options passed to cactusStreamTranscribeStart. The bias is applied for the lifetime of the stream session.

Embeddings¶

val embedding      = cactusEmbed(model, "Hello, world!", true)   // FloatArray
val imageEmbedding = cactusImageEmbed(model, "/path/to/image.jpg")
val audioEmbedding = cactusAudioEmbed(model, "/path/to/audio.wav")
Tokenization¶

val tokens = cactusTokenize(model, "Hello, world!")  // IntArray
val scores = cactusScoreWindow(model, tokens, 0, tokens.size, 512)
VAD¶

val result = cactusVad(model, "/path/to/audio.wav", null, null)
Diarize¶

val result = cactusDiarize(model, "/path/to/audio.wav", null, null)
Options (all optional): - step_ms (int, default 1000) — sliding window stride in milliseconds - threshold (float) — zero out per-speaker scores below this value - num_speakers (int) — keep only the N most active speakers - min_speakers / max_speakers (int) — speaker count bounds - raw_powerset (bool, default false) — return raw 7-class powerset scores instead of 3-speaker probabilities

Embed Speaker¶

val result = cactusEmbedSpeaker(model, "/path/to/audio.wav", null, null)

// With diarization mask for speaker-specific embedding
val result = cactusEmbedSpeaker(model, "/path/to/audio.wav", null, null, maskWeights)
Returns a 256-dimensional speaker embedding. When maskWeights (a per-frame weight array from diarization) is provided, the embedding is extracted using weighted stats pooling for speaker-specific embeddings.

RAG¶

val result = cactusRagQuery(model, "What is machine learning?", 5)
Vector Index¶

val index = cactusIndexInit("/path/to/index", 3)

cactusIndexAdd(
    index,
    intArrayOf(1, 2),
    arrayOf("Document 1", "Document 2"),
    arrayOf(floatArrayOf(0.1f, 0.2f, 0.3f), floatArrayOf(0.4f, 0.5f, 0.6f)),
    null
)

val resultsJson = cactusIndexQuery(index, floatArrayOf(0.1f, 0.2f, 0.3f), null)
cactusIndexDelete(index, intArrayOf(2))
cactusIndexCompact(index)
cactusIndexDestroy(index)
API Reference¶
All functions are top-level and mirror the C FFI directly. Handles are Long values.

Init / Lifecycle¶

fun cactusInit(modelPath: String, corpusDir: String?, cacheIndex: Boolean): Long  // throws RuntimeException
fun cactusDestroy(model: Long)
fun cactusReset(model: Long)
fun cactusStop(model: Long)
fun cactusGetLastError(): String
Prefill¶

fun cactusPrefill(
    model: Long,
    messagesJson: String,
    optionsJson: String?,
    toolsJson: String?,
    pcmData: ByteArray? = null
): String
Completion¶

fun cactusComplete(
    model: Long,
    messagesJson: String,
    optionsJson: String?,
    toolsJson: String?,
    callback: CactusTokenCallback?,
    pcmData: ByteArray? = null
): String
Transcription¶

fun cactusTranscribe(
    model: Long,
    audioPath: String?,
    prompt: String?,
    optionsJson: String?,
    callback: CactusTokenCallback?,
    pcmData: ByteArray?
): String

fun cactusStreamTranscribeStart(model: Long, optionsJson: String?): Long  // throws RuntimeException
fun cactusStreamTranscribeProcess(stream: Long, pcmData: ByteArray): String
fun cactusStreamTranscribeStop(stream: Long): String
Embeddings¶

fun cactusEmbed(model: Long, text: String, normalize: Boolean): FloatArray
fun cactusImageEmbed(model: Long, imagePath: String): FloatArray
fun cactusAudioEmbed(model: Long, audioPath: String): FloatArray
Tokenization / Scoring¶

fun cactusTokenize(model: Long, text: String): IntArray
fun cactusScoreWindow(model: Long, tokens: IntArray, start: Int, end: Int, context: Int): String
Detect Language¶

fun cactusDetectLanguage(model: Long, audioPath: String?, optionsJson: String?, pcmData: ByteArray?): String
VAD¶

fun cactusVad(model: Long, audioPath: String?, optionsJson: String?, pcmData: ByteArray?): String
Diarize¶

fun cactusDiarize(model: Long, audioPath: String?, optionsJson: String?, pcmData: ByteArray?): String
Embed Speaker¶

fun cactusEmbedSpeaker(model: Long, audioPath: String?, optionsJson: String?, pcmData: ByteArray?, maskWeights: FloatArray? = null): String
RAG¶

fun cactusRagQuery(model: Long, query: String, topK: Int): String
Vector Index¶

fun cactusIndexInit(indexDir: String, embeddingDim: Int): Long  // throws RuntimeException
fun cactusIndexDestroy(index: Long)
fun cactusIndexAdd(index: Long, ids: IntArray, documents: Array<String>, embeddings: Array<FloatArray>, metadatas: Array<String>?): Int
fun cactusIndexDelete(index: Long, ids: IntArray): Int
fun cactusIndexGet(index: Long, ids: IntArray): String
fun cactusIndexQuery(index: Long, embedding: FloatArray, optionsJson: String?): String
fun cactusIndexCompact(index: Long): Int
Logging¶

fun cactusLogSetLevel(level: Int)  // 0=DEBUG 1=INFO 2=WARN 3=ERROR 4=NONE
fun cactusLogSetCallback(callback: CactusLogCallback?)
Telemetry¶

fun cactusSetTelemetryEnvironment(cacheDir: String)
fun cactusSetAppId(appId: String)
fun cactusTelemetryFlush()
fun cactusTelemetryShutdown()
Types¶

fun interface CactusTokenCallback {
    fun onToken(token: String, tokenId: Int)
}

fun interface CactusLogCallback {
    fun onLog(level: Int, component: String, message: String)
}
Requirements¶
Android API 21+ / arm64-v8a
iOS 13+ / arm64 (KMP only)
See Also¶