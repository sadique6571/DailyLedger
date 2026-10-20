# Build 34 — category typing stays visible

Version remains 2.0.0. The shared category field now preserves unfinished search text when the suggestions close, the keyboard/back button is used, focus moves to another input, or Compose restores the saved field state. Tapping the editable field reopens suggestions instead of toggling them shut; the arrow separately opens/closes the list. Selecting a category, explicitly clearing it, or changing income/expense type resets the search as intended. The fix applies to manual entries, transaction editing, AI draft review, udhar and planning forms that use the shared field.

Manual transaction saving also requires a selected category, matching the other entry forms. An unfinished category search cannot silently save as Other. Users can select a search result or explicitly add their text as a custom category.

Five Android regressions exercise empty and Other selections, focus/back dismissal, saved state restoration, custom selection/clear, manual review/save, and inline AI review. Existing signing, shrinking, authentication, backup, encryption and calculations remain unchanged.
