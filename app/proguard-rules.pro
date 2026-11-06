# HabitCommit release rules.
#
# Compose, WorkManager and the AndroidX libraries ship their own consumer rules;
# the app itself uses no reflection. Keep only what the platform inflates by
# name from the manifest or from layout resources, which R8 already handles.
#
# JSON is parsed with org.json (platform classes, not renamed).
-keepattributes *Annotation*
