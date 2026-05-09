/**
 * Demo local authentication: BCrypt-hashed passwords on {@code los_users} and stateless
 * X-User-Id headers for existing APIs. <strong>Not suitable for production</strong> — use IAM/OAuth/JWT
 * (or real sessions) and email delivery for password reset links.
 */
package com.los.core.service.auth;
