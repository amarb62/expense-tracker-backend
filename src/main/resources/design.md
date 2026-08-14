# MASTER PROMPT — PERSONAL FINANCE & EXPENSE ANALYTICS BACKEND

You are a senior Java/Spring Boot architect and backend engineer.

Build a production-quality backend for a Personal Finance & Expense Analytics application.

The application allows users to:

1. Register and log in.
2. Add bank accounts and credit cards.
3. Upload bank/credit-card PDF statements.
4. Extract transactions from uploaded PDFs.
5. Automatically categorize expenses using a hybrid categorization engine.
6. Use AI/local LLM categorization for unknown transactions.
7. Manually add expenses and income.
8. Modify automatically assigned categories.
9. Remember user-specific merchant/category preferences.
10. Maintain monthly and yearly financial data.
11. Display dashboard analytics through REST APIs.
12. Track total credited amount, total expenses, remaining amount, and category-wise spending.
13. Prevent duplicate transactions.
14. Handle credit-card payments/transfers without double-counting expenses.

The backend must be designed so that financial calculations are deterministic and AI is used ONLY for transaction classification.

---

# 1. TECHNOLOGY STACK

Use:

* Java 21
* Spring Boot 3.x/latest stable compatible version
* Spring Web
* Spring Data JPA
* Hibernate
* Spring Security
* JWT authentication
* PostgreSQL
* Flyway database migrations
* Bean Validation
* Lombok only where it improves readability
* Apache PDFBox for PDF text extraction
* RabbitMQ for asynchronous statement processing
* Jackson
* OpenAPI/Swagger
* Maven
* Docker support
* JUnit 5
* Mockito
* Testcontainers for integration tests

Do NOT use microservices.

Build a modular monolith with clean separation of responsibilities.

---

# 2. PROJECT STRUCTURE

Use this package structure:

com.amar.expense_tracker

├── auth
├── user
├── account
├── statement
├── transaction
├── category
├── categorization
├── ai
├── analytics
├── dashboard
├── common
└── config

Each module should have appropriate:

* controller
* service
* repository
* entity
* dto
* mapper
* exception

Do not expose JPA entities directly from controllers.

Use DTOs for API requests and responses.

---

# 3. DATABASE

Use PostgreSQL.

Create Flyway migrations.

Required tables:

## users

Fields:

* id UUID primary key
* name
* email unique
* password_hash
* created_at
* updated_at

## accounts

Represents bank accounts and credit cards.

Fields:

* id UUID primary key
* user_id
* name
* institution
* account_type
* last_four_digits
* currency
* active
* created_at
* updated_at

account_type:

BANK_ACCOUNT
CREDIT_CARD
CASH
OTHER

## statements

Fields:

* id UUID
* user_id
* account_id
* file_name
* storage_key
* statement_type
* statement_start_date
* statement_end_date
* status
* uploaded_at
* processed_at
* error_message

status:

UPLOADED
PROCESSING
PROCESSED
FAILED

## transactions

Fields:

* id UUID
* user_id
* account_id
* statement_id nullable
* transaction_date
* description
* normalized_merchant
* amount
* transaction_type
* category_id nullable
* source
* confidence_score nullable
* transaction_hash unique
* is_manually_added
* is_category_modified
* created_at
* updated_at

transaction_type:

DEBIT
CREDIT
TRANSFER
REFUND
PAYMENT
FEE
INTEREST
CASH_WITHDRAWAL

source:

PDF
MANUAL
AI
RULE
SYSTEM

## categories

Fields:

* id UUID
* name
* parent_id nullable
* category_type
* active

Initial categories:

FOOD
GROCERIES
SHOPPING
TRANSPORT
FUEL
TRAVEL
RENT
UTILITIES
ENTERTAINMENT
HEALTHCARE
EDUCATION
INSURANCE
EMI
INVESTMENT
SUBSCRIPTION
ATM_CASH
BANK_CHARGES
TAX
TRANSFER
OTHER

Allow hierarchical categories using parent_id.

## user_category_rules

Fields:

* id UUID
* user_id
* merchant_pattern
* category_id
* priority
* created_at
* updated_at

## ai_categorization

Fields:

* id UUID
* transaction_id
* model_name
* predicted_category_id
* confidence
* reasoning
* status
* created_at

status:

AUTO_APPROVED
NEEDS_REVIEW
USER_APPROVED
USER_CORRECTED
FAILED

## monthly_summary

Fields:

* id UUID
* user_id
* year
* month
* total_credited
* total_expenses
* remaining_amount
* expense_percentage
* created_at
* updated_at

Unique constraint:

user_id + year + month

## category_monthly_summary

Fields:

* id UUID
* user_id
* year
* month
* category_id
* total_amount
* percentage_of_expense
* percentage_of_credit

Unique constraint:

user_id + year + month + category_id

---

# 4. FINANCIAL RULES

These rules are extremely important.

Do NOT allow AI to calculate financial totals.

The backend must calculate all totals from transactions.

Total credited:

SUM of valid CREDIT transactions.

Total expenses:

SUM of valid DEBIT transactions that represent actual expenses.

Do not count:

* transfers
* credit-card payments
* refunds incorrectly
* internal account transfers

as normal expenses.

For monthly analytics:

remaining_amount = total_credited - total_expenses

expense_percentage =
(total_expenses / total_credited) * 100

If total_credited is zero, expense_percentage must be zero.

Category percentage of credited amount:

(category_amount / total_credited) * 100

Category percentage of expenses:

(category_amount / total_expenses) * 100

Use BigDecimal for all monetary calculations.

Never use double/float for money.

---

# 5. AUTHENTICATION

Implement:

POST /api/v1/auth/register

POST /api/v1/auth/login

POST /api/v1/auth/refresh

POST /api/v1/auth/logout

Use:

* BCrypt or Argon2 password hashing
* JWT access token
* Refresh token
* Spring Security
* Stateless authentication

Access token should have configurable expiration.

Do not store passwords in plain text.

---

# 6. ACCOUNT APIs

Implement:

POST /api/v1/accounts

GET /api/v1/accounts

GET /api/v1/accounts/{id}

PUT /api/v1/accounts/{id}

DELETE /api/v1/accounts/{id}

Users must only access their own accounts.

---

# 7. STATEMENT UPLOAD

Implement:

POST /api/v1/statements/upload

Accept multipart/form-data.

Validate:

* file extension
* MIME type
* maximum file size
* PDF validity

Do not store PDF binary data directly in PostgreSQL.

Create a storage abstraction:

interface FileStorageService

Provide:

LocalFileStorageService

The architecture must allow future S3 implementation.

After upload:

1. Save statement metadata.
2. Set status PROCESSING.
3. Publish RabbitMQ event.
4. Return statement ID immediately.

Do not make the HTTP request wait for PDF processing.

---

# 8. PDF PROCESSING

Use Apache PDFBox.

Create:

StatementParser interface:

boolean supports(StatementMetadata metadata);

List<RawTransaction> parse(InputStream pdf);

Implement the architecture so additional bank parsers can be added later.

Example:

HdfcBankStatementParser
IciciBankStatementParser
SbiBankStatementParser
GenericStatementParser

Do not hardcode the entire application around one bank.

Use Strategy Pattern.

If the PDF cannot be parsed:

* mark statement FAILED
* save error message
* do not create partial transactions unless explicitly designed to do so

---

# 9. TRANSACTION NORMALIZATION

Normalize transaction descriptions.

Examples:

SWIGGY*ORDER12345
SWIGGY PVT LTD
SWIGGY INSTAMART PUNE

should be normalized to something such as:

SWIGGY

Create:

MerchantNormalizer

It should:

* uppercase text
* remove unnecessary transaction IDs
* remove extra whitespace
* remove irrelevant reference numbers
* normalize common merchant patterns

Keep the raw description unchanged in the database.

---

# 10. DUPLICATE DETECTION

Create transaction fingerprint using:

account ID
transaction date
amount
normalized merchant
transaction type

Generate SHA-256 hash.

Store it as transaction_hash.

Create a unique database constraint.

If the same statement is uploaded twice, transactions must not be duplicated.

---

# 11. CATEGORIZATION ENGINE

Implement this order:

1. User-specific category rule
2. Global merchant rule
3. Rule engine
4. AI classifier
5. OTHER

Architecture:

ExpenseCategorizationService

↓

UserCategoryRuleService

↓

RuleEngine

↓

ExpenseCategorizationAI

↓

ConfidenceEvaluator

↓

Transaction persistence

---

# 12. AI ABSTRACTION

Do not directly couple business logic to a specific AI provider.

Create:

interface ExpenseCategorizationAI

Method:

CategorizationResult categorize(
List<TransactionForCategorization> transactions
);

Create implementations:

LocalLLMCategorizer
OpenAICategorizer
GeminiCategorizer

Only implement the provider required for the initial version, but keep the interface extensible.

Configuration:

expense-ai:
enabled: true
provider: local
confidence:
auto-approve: 0.85
review: 0.60

The AI must receive only necessary transaction information.

Do not send:

* password
* account number
* full bank statement
* balance
* unrelated personal data

---

# 13. AI OUTPUT

Require structured JSON:

{
"category": "FOOD",
"confidence": 0.95,
"reason": "Merchant is a food delivery service"
}

Validate:

* category exists
* confidence between 0 and 1

If confidence >= 0.85:

AUTO_APPROVED

If confidence >= 0.60 and < 0.85:

NEEDS_REVIEW

If confidence < 0.60:

NEEDS_REVIEW

AI failures must not crash statement processing.

Fallback to OTHER and mark categorization FAILED if necessary.

---

# 14. USER FEEDBACK

When the user changes:

AMAZON → ELECTRONICS

create/update:

user_category_rules

So future transactions from the same merchant can be categorized without AI.

Priority:

USER_RULE

>

GLOBAL_RULE

>

AI

---

# 15. MANUAL EXPENSE

Implement:

POST /api/v1/transactions/expenses

Request:

{
"amount": 1000,
"date": "2026-08-09",
"description": "Dinner",
"categoryId": "...",
"accountId": "..."
}

Set:

is_manually_added = true

source = MANUAL

---

# 16. MANUAL INCOME

Implement:

POST /api/v1/transactions/income

Allow users to manually add:

* salary
* bonus
* freelance income
* other income

These must appear in total credited amount.

---

# 17. TRANSACTION APIs

Implement:

GET /api/v1/transactions

Support filters:

* fromDate
* toDate
* accountId
* categoryId
* transactionType
* source
* page
* size

GET /api/v1/transactions/{id}

PUT /api/v1/transactions/{id}

DELETE /api/v1/transactions/{id}

PATCH /api/v1/transactions/{id}/category

The category update endpoint must create/update the user's merchant rule when requested.

---

# 18. AI REVIEW APIs

Implement:

GET /api/v1/categorization/review

Return transactions that need user review.

Implement:

POST /api/v1/categorization/{transactionId}/approve

PATCH /api/v1/categorization/{transactionId}

Allow user to select another category.

---

# 19. DASHBOARD APIs

Implement:

GET /api/v1/dashboard/monthly

Parameters:

year
month
accountId optional

Response:

{
"period": "2026-08",
"totalCredited": 100000,
"totalExpenses": 60000,
"remaining": 40000,
"expensePercentage": 60,
"categories": [
{
"category": "FOOD",
"amount": 8000,
"percentageOfExpenses": 13.33,
"percentageOfCredit": 8
}
]
}

Implement:

GET /api/v1/dashboard/yearly

Parameters:

year
accountId optional

Return:

* total credited
* total expenses
* remaining
* expense percentage
* category breakdown
* monthly breakdown

Implement:

GET /api/v1/dashboard/trends

Return monthly credited/expense values.

---

# 20. MONTHLY DATA

The application must maintain data per month.

Example:

January 2026
February 2026
March 2026
...

Monthly summaries must be recalculated whenever transactions are:

* added
* deleted
* updated
* recategorized

Do not allow stale analytics.

Use a dedicated:

AnalyticsService

---

# 21. CREDIT CARD SPECIAL HANDLING

Credit card transactions must distinguish:

PURCHASE
PAYMENT
REFUND
FEE
INTEREST
CASH_WITHDRAWAL

A credit-card payment should not be counted as another expense if the original purchase is already recorded.

Avoid double counting.

---

# 22. ERROR HANDLING

Create global exception handling using:

@RestControllerAdvice

Standard response:

{
"timestamp": "...",
"status": 400,
"code": "INVALID_REQUEST",
"message": "...",
"path": "..."
}

Create meaningful domain exceptions.

---

# 23. SECURITY

Implement:

* user-level authorization
* ownership validation
* input validation
* file validation
* file size limits
* secure file storage
* no sensitive transaction logging
* no PDF contents in logs
* no password logging
* no JWT logging

A user must never access another user's:

* accounts
* statements
* transactions
* analytics
* uploaded files

---

# 24. TESTING

Write unit tests for:

* MerchantNormalizer
* RuleEngine
* CategorizationService
* ConfidenceEvaluator
* Financial calculation
* Duplicate detection

Write integration tests for:

* Authentication
* Account APIs
* Transaction APIs
* Statement upload
* Dashboard APIs
* PostgreSQL repository behavior

Use Testcontainers for PostgreSQL.

---

# 25. API DOCUMENTATION

Configure Swagger/OpenAPI.

Document:

* authentication
* request models
* response models
* errors
* query parameters

Add Bearer JWT authentication to Swagger.

---

# 26. DOCKER

Provide:

Dockerfile

docker-compose.yml

Docker Compose should start:

* PostgreSQL
* RabbitMQ
* backend

Use environment variables for secrets.

Never hardcode:

* JWT secret
* database password
* AI API key

---

# 27. OBSERVABILITY

Add:

* structured logging
* request correlation ID
* processing status
* statement processing duration
* categorization success/failure metrics

Do not log sensitive financial information.

---

# 28. CODE QUALITY

Follow:

* SOLID principles
* clean architecture principles
* dependency inversion
* Strategy Pattern for statement parsers
* Strategy Pattern for AI providers
* DTO pattern
* service/repository separation
* meaningful naming
* small methods
* no duplicated business logic

Do not over-engineer.

Do not create microservices.

---

# 29. EXPECTED OUTPUT

Generate the complete backend project including:

1. pom.xml
2. application.yml
3. application-local.yml
4. database migrations
5. entities
6. repositories
7. services
8. controllers
9. DTOs
10. mappers
11. security
12. JWT authentication
13. PDF processing
14. categorization engine
15. AI abstraction
16. RabbitMQ integration
17. dashboard APIs
18. exception handling
19. tests
20. Dockerfile
21. docker-compose.yml
22. README.md

The project must compile and run.

Do not provide pseudo-code for core functionality.

Generate actual implementation code.

Before generating code, create a concise implementation plan and project structure, then implement the project module by module.

All APIs must use /api/v1.

Use UUID identifiers.

Use BigDecimal for money.

Use UTC timestamps in the backend.

Make the frontend integration straightforward through well-defined JSON REST APIs.
