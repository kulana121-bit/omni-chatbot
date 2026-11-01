# omni-chatbot
Omni Chatbot — com.ravi.omnichat v1.9 (Kotlin/Compose)

## Features

- **Multi-provider AI chat** — OpenAI, Gemini aur Anthropic providers ek hi app se (ApiClient), streaming replies ke saath.
- **Auto-continue replies** — lamba jawab beech me kat jaye (token limit) to auto-continue, ya Continue chip dabakar aage badhao.
- **Connectors (Apps & Skills)** — @mention se connectors use karo: contacts search, location, device info, installed apps list (Connectors.kt, SkillLibrary.kt).
- **Sensitive tool safety** — contacts/location jaise sensitive tools ke liye ToolPolicy confirmation, khaas karke jab chat me untrusted content (web/file) aaya ho.
- **Web search** — jawabon ke saath web search support (WebSearch.kt).
- **Network security by default** — cleartext HTTP band (sirf localhost allowed), EndpointPolicy + NetGuard har endpoint ko gate karte hain; secrets SecretStore me aur logs Redactor se saaf.
- **Usage tracking** — per-request token usage ka hisaab (Usage.kt), bina double-count ke.
- **Markdown chat UI** — Compose UI, markdown rendering, settings aur Apps & Skills screens.
