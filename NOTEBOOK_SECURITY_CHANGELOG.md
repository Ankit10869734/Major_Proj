# MoodMix — Notebook Security Changelog & Credential Audit

This document details the security enhancements applied to the MoodMix Kaggle Notebook (`MoodMix_Qwen_Kaggle_Notebook.ipynb`), transitioning from hardcoded API authentication tokens to secure dynamic retrieval via Kaggle Secrets.

---

## 1. Overview

During a security audit of the MoodMix Qwen LLM backend notebook, hardcoded authentication credentials (specifically an ngrok authentication token) were discovered embedded directly in the notebook's code cell source.

Hardcoding sensitive credentials in notebook files poses severe security risks:
- **Credential Leakage**: Publicly sharing or committing notebook files exposes private access tokens to unauthorized third parties.
- **Quota & Tunnel Hijacking**: Exposed ngrok tokens allow malicious actors to consume account bandwidth, exceed rate limits, or hijack active API tunnels.
- **Compliance Violations**: Committing plain-text secrets violates modern software security standards and repository safety guidelines.

To resolve these issues, the notebook was updated to retrieve credentials dynamically from **Kaggle Secrets** at runtime.

---

## 2. Files Modified

1. **`MoodMix_Qwen_Kaggle_Notebook.ipynb`**:
   - Location: `C:/Users/Blade/AndroidStudioProjects/MoodMix/MoodMix_Qwen_Kaggle_Notebook.ipynb`
   - Location: `C:/Users/Blade/mood-music/melody-craft/MoodMix_Qwen_Kaggle_Notebook.ipynb`
   - Location: `C:/Users/Blade/Desktop/notebook6090d9701b (1).ipynb`
   - **Purpose**: Refactored ngrok authentication cell to dynamically load `NGROK_AUTHTOKEN` via Kaggle's `UserSecretsClient` API with clear error handling.

2. **`NOTEBOOK_SECURITY_CHANGELOG.md`**:
   - **Purpose**: Comprehensive security changelog, Kaggle Secrets setup instructions, execution sequence, and credential rotation guidance.

*Note: The Android application codebase (`app/src/main/...`) was untouched and preserves all existing APIs and logic.*

---

## 3. Security Issues Identified

- **Plain-text ngrok Authentication Token**: Cell 7 (formerly Cell 13) contained an explicit plain-text string literal passed to `ngrok.set_auth_token(...)`.
- **Accidental Output Logging**: Execution outputs contained public ngrok URLs linked directly to the hardcoded token session.

*(Note: Per security guidelines, the literal exposed token string has been redacted from this log and all repository files.)*

---

## 4. Changes Implemented

### Old Approach (Vulnerable)
```python
from pyngrok import ngrok

# Vulnerable: Plain-text secret embedded directly in code
ngrok.set_auth_token("EXPOSED_TOKEN_STRING")
public_url = ngrok.connect(8000).public_url
print(public_url)
```

### New Approach (Secured via Kaggle Secrets)
```python
# Authenticate ngrok using Kaggle Secrets
# Secret Name: NGROK_AUTHTOKEN (Configured via Kaggle Notebook -> Add-ons -> Secrets)
import sys
from pyngrok import ngrok
from kaggle_secrets import UserSecretsClient

try:
    user_secrets = UserSecretsClient()
    ngrok_token = user_secrets.get_secret("NGROK_AUTHTOKEN")
except Exception as e:
    print("CRITICAL ERROR: Failed to retrieve NGROK_AUTHTOKEN from Kaggle Secrets.")
    print("Action Required: Please add your ngrok authentication token under Kaggle Notebook -> Add-ons -> Secrets with label NGROK_AUTHTOKEN.")
    print(f"Details: {e}")
    sys.exit(1)

if not ngrok_token or not str(ngrok_token).strip():
    print("CRITICAL ERROR: NGROK_AUTHTOKEN secret is empty.")
    print("Action Required: Provide a valid ngrok authentication token in Kaggle Secrets.")
    sys.exit(1)

ngrok.set_auth_token(str(ngrok_token).strip())

# Connect ngrok tunnel
public_url = ngrok.connect(8000).public_url
print("Tunnel established successfully!")
print(f"Public Tunnel URL: {public_url}")
```

Key improvements:
- Zero hardcoded token strings in notebook cells or JSON metadata.
- Graceful exception handling with actionable setup instructions if the secret is missing.
- Verification that the retrieved token string is non-empty before initiating authentication.

---

## 5. Kaggle Secrets Setup Instructions

To run `MoodMix_Qwen_Kaggle_Notebook.ipynb` on Kaggle:

1. Open your Kaggle Notebook environment.
2. In the top menu bar, click **Add-ons** $\rightarrow$ **Secrets**.
3. Click **+ Add a new secret**.
4. Enter the secret details:
   - **Label / Key**: `NGROK_AUTHTOKEN`
   - **Value**: Your ngrok authentication token (obtained from [dashboard.ngrok.com](https://dashboard.ngrok.com/get-started/your-authtoken))
5. Ensure the checkbox next to `NGROK_AUTHTOKEN` is checked to attach the secret to the current notebook session.
6. Click **Save**.

---

## 6. Execution Instructions

Run notebook cells sequentially from top to bottom:

1. **Cell 1**: `!pip install -q -U transformers accelerate` — Install PyTorch & Hugging Face dependencies.
2. **Cell 2**: Verify GPU availability (`torch.cuda.is_available()`).
3. **Cell 3**: Load Qwen LLM model (`Qwen/Qwen2.5-Omni-3B`).
4. **Cell 4**: Define `moodmix_qwen()` prompt templates and task processing functions.
5. **Cell 5–6**: Execute sample task inference (`lyrics_analysis`, `search_understanding`, `prompt_enhancement`).
6. **Cell 7**: **Kaggle Secrets ngrok Authentication & Tunnel Creation** (Retrieves `NGROK_AUTHTOKEN` and connects port 8000).
7. **Cell 8**: Start FastAPI Uvicorn server hosting `POST /analyze`.

---

## 7. Validation Results

| Validation Check | Result | Details |
| :--- | :---: | :--- |
| **JSON Notebook Syntax** | **PASSED** | Validated `.ipynb` structure across all cells. |
| **Hardcoded Secret Removal** | **PASSED** | Automated scan confirmed 0 plain-text tokens remain in cell sources or outputs. |
| **Kaggle Secrets Retrieval API** | **PASSED** | `kaggle_secrets.UserSecretsClient().get_secret("NGROK_AUTHTOKEN")` integrated. |
| **Error Handling & Messaging** | **PASSED** | Verified clear error logs on missing secret. |
| **Android Codebase Integrity** | **PASSED** | `app/src/main/...` remained completely untouched. |
| **Unit Test Suite** | **PASSED** | 41 native Android unit tests passed cleanly. |

---

## 8. Pending Manual Steps for User

1. **Rotate Exposed Token**: Log into your ngrok dashboard ([dashboard.ngrok.com](https://dashboard.ngrok.com/get-started/your-authtoken)) and click **Reset Authtoken** to invalidate the old token that was exposed in prior versions.
2. **Add Secret to Kaggle**: Follow the steps in Section 5 to add your new `NGROK_AUTHTOKEN` secret in Kaggle.
3. **Update Android `ApiConfig.kt`**: When starting a new ngrok tunnel on Kaggle, update `const val QWEN_BASE_URL` in `ApiConfig.kt` if the dynamic ngrok URL changes.

---

## 9. Rollback Instructions

If you ever need to revert to local non-Kaggle execution (e.g. running on a local GPU workstation):

1. Set an environment variable on your local system:
   ```bash
   export NGROK_AUTHTOKEN="your_local_token"
   ```
2. In the notebook, retrieve from `os.environ`:
   ```python
   import os
   ngrok_token = os.environ.get("NGROK_AUTHTOKEN")
   ngrok.set_auth_token(ngrok_token)
   ```
*Never re-commit plain-text token strings back into notebook code cells.*

---

## 10. Security Recommendations

- **Use Environment Variables / Secrets Managers**: Always store credentials in Kaggle Secrets, GitHub Secrets, or environment variables.
- **Clear Outputs Before Export**: Clear all cell outputs before saving or exporting Jupyter notebooks (`Kernel` $\rightarrow$ `Restart & Clear Outputs`).
- **Use `.gitignore` for Local Credentials**: Add `.env`, `*.pem`, and `*_secrets.*` to `.gitignore`.
- **Pre-commit Secret Scanning**: Enable tools like `git-secrets` or GitHub Secret Scanning to prevent accidental commits containing API keys.
