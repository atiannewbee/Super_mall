# SUPER MALL Login Page Design

The login page is a dedicated `/login` route that preserves the storefront's editorial technology aesthetic. A dark-blue campaign panel communicates membership value while a calm white form surface supports password and SMS-code sign-in. The page is responsive, keyboard accessible, and explicit that authentication is currently a frontend demonstration.

Password login accepts a phone number or email plus a minimum six-character password. Code login accepts a mainland China mobile number and a six-digit code, with a local countdown preview. Validation is inline and no password or verification code is persisted. A successful demo login stores only a small non-sensitive session descriptor in localStorage, returns to the storefront, and updates the header account state.

The later backend contract will replace `loginDemo` with Spring Security endpoints, password hashing, short-lived access sessions, rate-limited verification-code delivery, login audit events, and MySQL user/account tables.
