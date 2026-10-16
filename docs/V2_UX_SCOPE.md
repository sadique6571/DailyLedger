# DailyLedger 2.0 — UX Upgrade Tracking

Status: **historical candidate backlog**. The selected 16 October batch is tracked in
[V2_RELEASE_CHECKLIST.md](V2_RELEASE_CHECKLIST.md), with verification in [TESTING.md](../TESTING.md).
The list below records earlier ideas, not a declaration that all 151 features shipped.

## Safety / release rules
- Preserve Google Drive backup/restore, Room/SQLCipher database schemas/migrations, Firebase configuration and ledger calculations. The approved October 16 batch adds AI-learning controls and includes Udhar payments in the AI summary; it does not replace the Firebase transport or change ledger math.
- Preserve application ID, signing certificate and upgrade compatibility.
- Preserve all existing records; use UI-local settings only for display preferences.
- No merge into main until complete, reviewed, and tested.
- Keep prior released APK/version untouched. Assign versionName `2.0.0` and increase versionCode only at final release candidate stage.
- No v2 PR until development batch is ready for final CI; opening a PR to main triggers CI automatically.
- Final CI must run JVM tests, Android emulator UI tests, release APK verify, and signing check.

## V2 scope — 121 candidate UI features
Implement and verify or explicitly mark each deferred, with a regression test and accessibility criteria where meaningful.

### Dashboard 1–15
1. Drag/drop cards
2. Layout presets
3. Collapsible sections
4. Favorite/pinned cards
5. Card transparency
6. Card corner radius
7. Card shadows
8. Card borders
9. Custom card accent colors
10. Icon visibility
11. Quick actions
12. Balance privacy shortcut
13. Refresh indication
14. Expandable summaries
15. Layout reset

### Appearance 16–30
16. Font families
17. Font weight
18. Heading size
19. Amount size
20. Filled/outlined icons
21. Icon background shape
22. Icon color scheme
23. Button size
24. Button shape
25. Navigation bar size
26. Card padding
27. High contrast
28. Larger touch targets
29. Accessibility presets
30. Live preview (existing basic implementation; audit)

### Transactions 31–45
31. Favorite transactions
32. Recent categories
33. Quick amounts (first implementation in v2 branch)
34. Duplicate warning
35. Undo delete
36. Swipe actions
37. Date grouping
38. Sticky month headers
39. Advanced search
40. Match highlighting
41. Sorting (first implementation in v2 branch)
42. Multi-select
43. Details view
44. Expandable notes (first implementation in v2 branch)
45. Empty-search suggestions

### Forms 46–57
46. Calendar picker
47. Today/yesterday shortcuts
48. Calculator amount keypad
49. Keyboard focus
50. Error auto-scroll
51. Unsaved changes warning
52. Save and add another
53. Save confirmation
54. Required field indications
55. Searchable category picker
56. Note templates
57. Simple/detailed form layouts

### Navigation 58–67
58. Custom bottom tabs
59. Nav icon labels
60. Tap Home scroll-to-top
61. Global quick add
62. Scroll restoration (existing partially)
63. Transition preference
64. Back gesture consistency
65. Global search entry
66. Quick jump menu
67. One-hand mode

### Reports 68–77
68. Monthly comparison
69. Expense charts
70. Chart types
71. Chart colors
72. Chart labels
73. Expandable breakdown
74. Date range picker
75. Export preview
76. Android share sheet
77. Export history shortcuts

### Financial-section presentation 78–89
78. Section headers
79. Compact/detailed list mode
80. Status chips
81. Section search
82. Payment history expansion
83. Progress indicators
84. Helpful empty states
85. Persistent add actions
86. Chronological timelines
87. Name/date sorting
88. Status filters
89. Detail page sections

### Privacy/accessibility 90–101
90. Full-app amount masking (existing partial; audit every screen)
91. Screenshot protection
92. Privacy shortcut
93. Recents preview privacy
94. Large text preset
95. Contrast preset
96. Reduce motion
97. Optional haptics
98. TalkBack labels
99. Touch target minimums
100. Keyboard navigation
101. Accessible noncolor status

### Performance/stability 102–111
102. Long-list lazy rendering
103. Stable item keys
104. Recomposition audit
105. Chart rendering
106. Motion performance
107. Loading placeholders
108. Error states
109. Offline status display
110. Low-memory handling
111. Startup benchmarks

### UX extras 112–121
112. Greeting options
113. English/Urdu interface
114. Favorite shortcuts
115. Onboarding tips
116. What's New view
117. Context help
118. Settings search
119. Individual setting resets
120. Display diagnostics
121. Feedback/bug reporting UI

## Additional requests to include
- Duplicate detection, undo delete, favorite transactions, recent categories, quick amounts, expandable notes
- Pins/expandable dashboard cards, monthly comparison, presets
- Optional haptics, language, configurable home shortcuts, suggestions, unsaved-form warnings, confirmations
- Offline indicator, large-history speed, screenshot protection, accessibility presets
- Final UI regression coverage across multiple screen sizes and large text


## Additional approved UX improvements 122–151 (not all implemented)
122. Dashboard lock mode
123. Read-only chart mode — basic donut interaction removed in v2
124. Custom category symbols
125. Stable category colors — donut currently derives deterministic color from category label
126. Recent action history
127. Confirm before overwrite
128. Smart empty states
129. Custom dashboard header
130. Per-screen font preferences
131. Dashboard grid density
132. Compact transaction rows — initial local toggle implemented
133. Transaction details and copying — details initially implemented, copying pending
134. Expand/collapse all
135. Filter presets
136. Search history clearing
137. Payment status iconography
138. Theme accent picker
139. OLED black theme
140. Keep screen awake during entry
141. Keyboard Done flow
142. Currency numeric spacing
143. Card-specific amount visibility
144. Haptic intensity/off
145. Horizontal scroll affordances
146. Consistent confirmation styles
147. Contextual tooltips
148. Clear active filters — initial implementation
149. Dashboard section dividers
150. Robust long-name wrapping/truncation
151. UI settings preview/export

## Current feature implementation ledger
- Implemented on v2 branch but **not compiled/tested**: transaction sort, quick amounts, note expansion, duplicate guard, unsaved changes prompt, quick dates, amount/date search, compact rows, transaction details, clear filters, read-only donut/chart legend, More menu deduplication.
- Existing from v1.x and requiring audit: dashboard size preferences, privacy masking of selected screens, theme, some charts and navigation.
- Everything else above is pending implementation and verification. Scope inclusion does NOT mean implementation.

## Implementation update — screenshot protection
- Optional screenshot and Android recents-preview protection is implemented using FLAG_SECURE and a local UI preference; no database or backup modifications.
- This feature remains uncompiled and untested at the user-requested final test stage.
- Still pending: full undo-delete semantics, complete drag/drop and dashboard layout options, comprehensive Urdu localization, all-screen privacy coverage, accessibility and 151-item acceptance verification.

## Definition of done
- Every selected scope item has status Done + proof/test or is honestly documented Deferred.
- No data regression, signing change or broken in-place upgrade.
- Responsive layouts at compact devices, enlarged system fonts and dark mode.
- Single final GitHub Actions run after batch implementation, followed by on-device smoke test.
