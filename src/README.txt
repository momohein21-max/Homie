HOMIE - FORGOT PASSWORD (+ two Postgres bugs caught along the way)
=======================================================================

15 files. Covers three things:
  1. A full self-service "forgot password" flow (what you asked for)
  2. A silent login bug the MySQL -> Postgres migration introduced
  3. A profile-picture bug the same migration introduced

Both bugs (2 and 3) hadn't caused any visible error yet, but would have
eventually - I found them while reading through the code to build the
password reset feature, so I've fixed them now rather than waiting for
them to show up as confusing mystery bugs later.


WHERE EVERYTHING GOES
-------------------------
pom.xml                                          -> project root
application.properties                           -> src/main/resources/
main/java/.../entity/User.java                   -> entity package
main/java/.../repository/UserRepository.java     -> repository package
main/java/.../service/CustomUserDetailsService.java -> service package
main/java/.../service/UserService.java           -> service package
main/java/.../service/EmailService.java          -> service package (NEW)
main/java/.../controller/DashboardController.java   -> controller package
main/java/.../controller/PasswordResetController.java -> controller package (NEW)
main/java/.../config/SecurityConfig.java         -> config package
main/java/.../dto/ForgotPasswordDto.java         -> dto package (NEW)
main/java/.../dto/ResetPasswordDto.java          -> dto package (NEW)
main/resources/templates/login.html              -> templates
main/resources/templates/forgot-password.html    -> templates (NEW)
main/resources/templates/reset-password.html     -> templates (NEW)

Drag everything in, overwriting the files that already exist.


======================================================================
PART 1: THE TWO BUGS I FOUND (fix these even if you don't want email yet)
======================================================================

BUG A - Login could silently fail after the Postgres migration
--------------------------------------------------------------------
MySQL compares text case-INsensitively by default, so
"momo@Gmail.com" and "momo@gmail.com" were always treated as the same
email. PostgreSQL compares text case-SENSITIVELY by default. That means
after moving to Postgres, if you ever typed your email with different
capitalisation than how it's stored, login would fail with "wrong email
or password" even though the password was right.

Fixed by changing every email lookup (login, registration, checking for
duplicate emails) to explicitly ignore case. This touches
UserRepository.java, CustomUserDetailsService.java, UserService.java,
and DashboardController.java - all included here.

BUG B - Profile pictures would eventually break
--------------------------------------------------------------------
User.java told the database to store profile pictures using a type
called "LONGBLOB" - that's a MySQL-only type name. PostgreSQL has never
heard of it. This hadn't caused a visible crash yet because Hibernate's
auto-schema-update seems to have let table creation slide through, but
it was a ticking time bomb: the moment someone tried to actually upload
a NEW profile picture against a freshly Postgres-backed column, it very
likely would have failed.

Fixed by removing that MySQL-specific instruction entirely and letting
Hibernate pick whichever binary storage type is correct for whichever
database you're actually using - PostgreSQL in this case.


======================================================================
PART 2: FORGOT PASSWORD - WHAT YOU ASKED FOR
======================================================================

How it works
----------------
1. Housemate clicks "Forgot password?" on the login page
2. Types their email, clicks "Send reset link"
3. They ALWAYS see the same "check your email" message, whether or not
   that email actually belongs to an account - this is deliberate: it
   stops someone from using this form to figure out which email
   addresses are registered
4. If the email did match an account, a real email goes out with a
   one-time link, valid for 30 minutes
5. Clicking it lets them choose a new password
6. The link stops working immediately after use (or after 30 minutes,
   whichever comes first)

New database columns: reset_token and reset_token_expiry on the users
table, created automatically like everything else has been.


ONE THING YOU MUST DO BEFORE THIS WORKS: set up a Gmail App Password
--------------------------------------------------------------------------
Sending real emails needs real email-sending credentials. Since you
already have momohein21@gmail.com, we're using Gmail's own mail server
rather than signing up for a separate service.

Gmail won't accept your normal Gmail password for this - it needs a
special 16-character "App Password" instead. Steps:

  1. Go to myaccount.google.com/security
  2. Turn ON "2-Step Verification" if it isn't already on (Gmail
     requires this before it'll let you create an App Password)
  3. Once that's on, go to myaccount.google.com/apppasswords
  4. It'll ask you to name the app password - type "Homie"
  5. Click Create. Gmail shows you a 16-character code like:
       abcd efgh ijkl mnop
     (spacing doesn't matter - you can include or remove the spaces)
  6. Open application.properties and find this line:
       spring.mail.password=${MAIL_APP_PASSWORD:MAIL_APP_PASSWORD}
     Replace the SECOND "MAIL_APP_PASSWORD" (after the colon) with the
     16-character code Gmail gave you, e.g.:
       spring.mail.password=${MAIL_APP_PASSWORD:abcdefghijklmnop}

That's the only manual step. Everything else (SMTP host, port, etc.) is
already filled in for Gmail.


ONE THING TO UPDATE ONCE YOUR SITE IS LIVE ON RENDER
---------------------------------------------------------
application.properties has this line:
    app.base-url=${APP_BASE_URL:http://localhost:8080}

This is what reset links are built from. Right now it points at your
local machine, which is fine for testing locally. Once your Web Service
is live on Render with a real https://....onrender.com address, change
the fallback value to that address, e.g.:
    app.base-url=${APP_BASE_URL:https://homie-xxxx.onrender.com}
Otherwise, reset emails sent from the LIVE site would contain links
pointing at "localhost", which only works on your own machine.


TRYING IT OUT
-----------------
1. Set your Gmail App Password (above), drag all 15 files in, restart
2. Go to the login page, click "Forgot password?"
3. Type an email that's actually registered in your database, submit
4. You should see "Check your email"
5. Check that inbox - you should get a real email within a few seconds,
   with a subject like "Reset your Homie password"
6. Click the link in the email
7. Type a new password twice, submit
8. You should land back on the login page with "Your password has been
   changed. Please log in." - log in with the new password to confirm

If no email arrives within a minute or two, the most common cause is
the App Password not being set correctly - double check step 6 above,
and check IntelliJ's Run console for any error mentioning
"authentication failed" or "535", which both point at the same thing.
