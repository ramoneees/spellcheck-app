# spellcheck-app

📘 AI-Powered Speaker Notes Spell Checker

A hybrid baseline+LLM spell-checking system optimized for slide speaker notes, built with Clojure / ClojureScript, re-frame, Compojure-API, Swagger, and Docker.

⸻

⚙️ Setup Instructions

1. Clone the repository

git clone <[your-repo-url](https://github.com/ramoneees/spellcheck-app)>
⸻

Backend Setup (Clojure + Leiningen)

Requirements
	•	Java 17+
	•	Leiningen
	•	OpenAI API key (optional but required for advanced mode)

Environment Variable

export OPENAI_API_KEY="sk-your-key"

Run via REPL (recommended for dev)

lein repl
user=> (go)   ;; starts Jetty server on port 3000

Run via Leiningen

lein run


⸻

Frontend Setup (ClojureScript + shadow-cljs + re-frame)

Install dependencies

npm install

Start frontend dev server

npx shadow-cljs watch app

Frontend runs at:

http://localhost:8280

Backend runs at:

http://localhost:3000


⸻

Frontend → http://localhost:8280
Backend → http://localhost:3000/api/swagger

⸻

🧱 Architecture Overview

High-Level Flow

Frontend (re-frame)
   ↓ sends JSON payload
Backend route (/api/spellcheck)
   ↓ decides mode (baseline / AI / auto)
Spellchecker Pipeline
   ↓ baseline detector (dictionary + Levenshtein)
   ↓ glossary merging (tech + deck-specific)
   ↓ heuristics (llm-needed?)
   ↓ optional OpenAI call
   ↓ suggestions → offsets → frontend

Core Components
	•	Baseline Spellchecker
	•	Dictionary-based unknown word detection
	•	Levenshtein suggestion generation
	•	Glossary-aware filtering (tech glossary + deck glossary)
	•	LLM Spellchecker
	•	OpenAI model for grammar + clarity + contextual corrections
	•	Custom prompt designed for speaker notes style
	•	Auto Mode
	•	Runs baseline first, then applies heuristics:
	•	Many errors?
	•	Weak candidates?
	•	Very long unknown token?
	•	Long text with no baseline errors?
	•	Calls LLM only when needed → cost-efficient
	•	Glossary System
	•	Loads domain terms from CSV (resources/glossaries/glossary.csv)
	•	Merges domain glossary + deck glossary per request
	•	Ensures products, acronyms, and industry terms are never incorrectly “fixed”
	•	Frontend
	•	Notes editor + context editor
	•	Suggestions panel with badges (AI vs Baseline)
	•	Highlighted inline preview using token offsets
	•	Accept / reject flows
	•	Swagger API
	•	Automatic documentation via Compojure-API

⸻

✍️ Written Component

1. Approach and Key Design Decisions

Hybrid architecture

A pure LLM-based spellchecker is powerful but expensive, high-latency, and occasionally hallucinatory.
A pure dictionary-based spellchecker is fast but lacks context and grammar ability.
So we combine both.

Baseline first, AI second

Running the baseline detector before AI gives us:
	•	Fast response for simple typos
	•	Lower OpenAI usage cost
	•	Better reliability (fallbacks work)
	•	Deterministic behavior around glossary terms

Glossary support

Speaker notes almost always contain:
	•	Technical terms
	•	Acronyms
	•	Brand names
	•	Proper nouns

Traditional spellcheckers mark these as incorrect.
LLMs sometimes rewrite them incorrectly.

A glossary:
	•	Prevents false positives
	•	Prevents hallucinated “corrections”
	•	Helps the model interpret domain-specific language

This is a major product differentiator.

Auto-Mode (llm-needed?)

We implemented heuristics so the system knows when to escalate to AI:
	•	Large texts with no typos → grammar case
	•	Ambiguous baseline suggestions → LLM
	•	Very long weird words → LLM
	•	Many typos → LLM

This keeps cost predictable while delivering high accuracy.

⸻

2. Trade-offs Considered

Accuracy vs. Speed
	•	Baseline mode is extremely fast (ms), but limited.
	•	LLM mode is slower but provides grammar, clarity, and contextual corrections.
	•	Auto-mode gives the best balance:
	•	Use baseline for easy wins
	•	Use AI when needed

Cost vs. Quality
	•	Calling the LLM on every request is expensive.
	•	Auto-mode reduces cost by only escalating in difficult cases.
	•	Glossary reduces unnecessary LLM calls because domain terms don’t trigger suspicion.

Complexity vs. Maintainability
	•	Adding heuristics increases code complexity.
	•	But it dramatically reduces unnecessary LLM traffic.
	•	The system remains easy to extend because components are isolated:
	•	spellcheck.clj → logic
	•	ai.clj → model call
	•	glossary.clj → loader
	•	events.cljs → FE behavior

⸻

3. How This Could Be Extended for Production

Scalability
	•	Run backend behind load balancer, scale horizontally.
	•	Add persistent cache of LLM results to reduce repeat cost.
	•	Move dictionary and glossary into real DB or Redis.
	•	Use job queue for long-running LLM calls.

Privacy
	•	PII scrubbing before sending notes to LLM.
	•	On-prem or private LLM model for sensitive customers.
	•	Encryption in transit + at rest.
	•	Add audit logs to see what text was checked.

Offline Support
	•	Baseline mode already supports offline operation.
	•	Deploy a small local model (e.g., Llama 3.1 8B quantized) for offline LLM capability.
	•	Cache glossaries + dictionary locally.

Enterprise Integration
	•	Add authentication (OAuth2 / JWT).
	•	Track usage per team or deck.
	•	Provide API rate limiting and quotas.

⸻

### 🌟 Future AI Enhancements

#### **1. Speaker Notes Rewrite Mode**
LLM rewrites entire notes to be:
- more concise,
- more natural for speaking,
- rhythmically structured,
- audience-appropriate.

#### **2. Tone & Delivery Analyzer**
AI analyzes notes to detect:
- passive voice,
- overly complex sentences,
- lack of narrative flow,
- missing transitions.

Could suggest improvements like:

> “This part would benefit from a clearer call-to-action.”

#### **3. Slide-to-Notes Consistency Checker**
Given slide content (title + bullets + visuals), the AI:
- checks if notes accurately reflect the slide,
- detects missing explanations,
- provides suggestions like:

> “You mentioned Kafka Streams but the slide is about Schema Registry.”

#### **4. AI Cost Estimation & Optimization Layer**
Introduce a lightweight cost-estimator that calculates approximate token usage for each LLM request and returns a field like `aiCostEstimate` alongside suggestions. This allows:
- users to understand when and why AI was invoked,
- teams to monitor and optimize LLM spend,
- auto-mode to incorporate cost constraints into its decision-making.

This creates transparency around computational cost and aligns the feature with real-world scalability and budget considerations.

### **Evaluation Strategy**

To evaluate this feature, I would build a curated benchmark of speaker notes paired with ground-truth corrections, covering different scenarios: simple typos, grammar issues, domain-specific glossary terms, and deliberately tricky edge cases. For each model configuration (baseline only, AI only, and auto-mode), I would run the entire benchmark and measure how often the system produces the expected corrections, targeting at least ~95% accuracy on the test set. Concretely, I’d track precision and recall over suggested changes (how many of the suggestions are actually correct, and how many real errors we miss), as well as user-centric metrics such as the ratio of accepted vs. rejected suggestions and the number of edits needed after using the tool. This combination of offline benchmarking plus behavioral metrics from real usage gives a robust picture of effectiveness: we can confidently compare approaches, tune thresholds, and ensure that improvements to the model or heuristics actually translate into fewer errors and less friction for presenters.
