# v1.4.7 / 158 — series fixed-time editing and content-sized chat

Approved scope: finish the previously prepared series due-time fix, fit chat
bubbles to their content, preserve other layouts/features, publish v1.4.7.

All open updates linked unfinished fixed times and future template time. Saving
an already corrected occurrence with unchanged shared fields reapplies its title
and time. From here updates unfinished dates at/after the selected occurrence.
Each date gets a recalculated instant in the selected app clock. Completed and
cancelled times/private fields are retained; unrelated same-name tasks stay
separate. Existing one-occurrence confirmation remains for date/repeat/private
changes and Anytime/fixed-mode switches. Version-checked atomic writes retained.

Chat message bodies wrap to content instead of filling the 87% row. The existing
maximum width, left/right alignment, photos, reply quotes, context, reactions,
delivery status and composer remain. Bubble padding is 12/8 dp. A rendered native
synthetic chat was inspected. Tests assert short/long widths, wrapping height,
maximum width, both alignments, reply/emoji sizing and local/paired rendering.

Validation:

- Full 288 local tests passed, zero failures/errors/skips; debug build passed.
- Prepared server generator occupancy correction deployed to existing Supabase
  project. No task records, membership policies, grants or notification ownership
  rewritten. Five SQL date cases passed after deployment.
- Function remains private with fixed empty search_path; anon/authenticated lack
  direct execution permission. Baseline/post security-advisor results unchanged.
- Previous generator definition saved locally in generator-before.sql.
- Optional full synthetic live series-time transaction confirmation expired and
  did not execute; zero synthetic accounts found afterward. Prepared test script
  remains local for future execution. No live two-device acceptance test run.

Publication, CI full lint/tests and signed artifact verification pending.
