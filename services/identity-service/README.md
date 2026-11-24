# identity-service

Scaffolded bounded context for CatalogStudio microservices extraction.

## Owns (target)
Auth, users, OTP, referral, business/workspace, roles/access

## Future tables
users, refresh_tokens, otp_challenges, referral_codes, businesses, feature_access

## Status
Not yet extracted from `catalog-studio-backend`. Traffic remains on platform-api until this service's extract wave.

## Do not
- Copy business logic from platform-api into this module until the extract wave begins
- Create duplicate public APIs or database tables while platform still owns the domain
