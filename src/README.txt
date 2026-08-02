HOMIE - NOTIFICATION BUG FIX + BILL DELETION FIX + DEMO BUTTON
=======================================================================

8 files. Covers three things, most important first:

  1. REAL BUG FIX: notifications were silently failing to insert at all
     (see below) - this is the actual reason nothing ever showed up on
     the bell or arrived by email tonight
  2. A house-scoping fix to BillService.deleteAllForUser (minor,
     found while reviewing the account-deletion flow)
  3. A demo-only "Send bill reminders now" button on the Bills page


WHERE EVERYTHING GOES (all REPLACE existing files except DemoController)
--------------------------------------------------------------------------
Notification.java        -> main/java/.../entity/          (REPLACES existing)
DemoController.java      -> main/java/.../controller/      (NEW FILE)
BillRepository.java      -> main/java/.../repository/      (REPLACES existing)
BillService.java         -> main/java/.../service/          (REPLACES existing)
BillReminderScheduler.java -> main/java/.../service/        (REPLACES existing)
BillPaymentRepository.java -> main/java/.../repository/     (REPLACES existing)
ProfileController.java   -> main/java/.../controller/      (REPLACES existing)
BillServiceTest.java     -> test/java/.../service/          (REPLACES existing)
bills.html               -> main/resources/templates/       (REPLACES existing)

Drag everything in, overwriting the files that already exist.


======================================================================
PART 1: THE REAL BUG - notifications were never actually saving
======================================================================

WHAT WAS WRONG
--------------------------------------------------------------------
The Notification entity's createdDate field had no explicit @Column
mapping, so Hibernate used its default naming convention and tried to
insert into a column called created_date. Your actual Supabase table
has that column named created_at instead (NOT NULL, no default).

Every single attempt to save a notification - whether from a bill
reminder, or any other feature that might use NotificationService in
future - was hitting a database error:

  ERROR: null value in column "created_at" of relation "notifications"
  violates not-null constraint

This explains everything from tonight: the bell never lighting up,
no emails going out even when the bill-matching logic was 100% correct
(it was - we confirmed the query found the right bills and the right
housemates every time). The insert into notifications was silently
failing underneath it, which meant the code never even reached the
email-sending line.

THE FIX
--------------------------------------------------------------------
Notification.java now has:
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdDate;

This tells Hibernate to write to the real column name (created_at)
while keeping the Java field name (createdDate) exactly as it was -
so nothing else in the codebase needs to change. The repository's
findTop20ByUserOrderByCreatedDateDesc query still works unchanged,
since Spring Data derives that from the field name, not the column.

WORTH MENTIONING IN YOUR REPORT
--------------------------------------------------------------------
This is a genuinely good thing to write up honestly: it's a real bug
you found and fixed through methodical debugging (adding temporary
diagnostic logging, checking raw database values against what the UI
displayed, ruling out several other theories first). That process -
and the fact that the underlying reminder/query logic was correct the
whole time - is worth a sentence or two in your testing/evaluation
section.


======================================================================
PART 2: THE BILL-DELETION SCOPING FIX (minor, found earlier tonight)
======================================================================

BillService.deleteAllForUser used to call billRepository.findAll() -
every bill in the entire app, not just the departing housemate's own
house. Not a visible bug at your current scale (one house), but
inconsistent with how every other bill query in the app is scoped.
Fixed to take a House parameter and use a new findByHouse(House) query
instead. ProfileController and BillServiceTest updated to match.


======================================================================
PART 3: DEMO BUTTON - "SEND BILL REMINDERS NOW"
======================================================================

Still included and still useful, now that the real bug is fixed -
this lets you demo the reminder system on demand instead of waiting
for the 8am scheduled job. Go to Bills page -> "Add a bill" with a
due date of today/yesterday/tomorrow -> open "Demo: send bill
reminders now" -> click "Send reminders now". Check the bell and your
email - both should now actually work.

Worth mentioning in your report/demo that this is a demo aid calling
the same real method the scheduled job uses, not a separate system.
