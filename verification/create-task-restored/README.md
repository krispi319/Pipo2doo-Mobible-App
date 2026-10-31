# Restored Create task

New tasks now open the existing full task editor directly. The earlier quick-add
sheet was the cause of the simplified title box/text presentation; full task options
were previously behind More options. The quick-entry branch is no longer used.

The full editor retains its draft saver, title validation, three assignment choices,
list selection/creation, dates, time, repeat, tags, reminder settings, private note,
failure handling, and save callback. Editing recurring tasks retains occurrence scope.

Preview: create-task.png (native Compose, 360dp phone width, sample profiles).
UI/save/avatar checks: ui-test-results.csv. Draft checks: draft-test-results.csv.
Source changes are local and not yet included in a published APK.
