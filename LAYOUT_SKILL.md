# VCode Layout Design System — AI Skill Reference

> This file documents every layout pattern, value, and convention used in VCode's 47 layout XML files.
> Use this as the authoritative reference when creating or editing any layout in this project.

---

## 1. Typography

### Font Families

| Font | Usage |
|------|-------|
| `monospace` | Code display (editor tabs, diffs, console, commit SHAs, preview logs) |
| `sans-serif` | Standard body text (file tree names, find/replace, error messages) |
| `sans-serif-medium` | Semi-emphasized text (snippet titles, editor tab file names, error titles) |
| `sans-serif-black` | Badges (language badges on tabs, file tree, snippets) |

### Text Sizes

| Size | Usage |
|------|-------|
| `8sp` | File tree language badge |
| `9sp` | Editor tab language badge |
| `10sp` | Autocomplete detail, git file path/status, console count, commit HEAD badge, snippet badge |
| `11sp` | Diff line content |
| `11.5sp` | Console log text |
| `12sp` | Preview URL bar, find/replace toggles/match count, snippet preview |
| `13sp` | Autocomplete label, editor tab name, file tree name |
| `14sp` | Error message body, find/replace inputs, snippet title |
| `15sp` | Popup menu title, GitHub account title |
| `18sp` | Editor project name, delete confirmation title, preview error title |

### Material3 Text Appearances

| Style | Usage |
|-------|-------|
| `TextAppearance.Material3.ActionBar.Title` | App bar titles in activities |
| `TextAppearance.Material3.TitleLarge` | Bottom sheet main titles |
| `TextAppearance.Material3.TitleMedium` | Toolbar titles, empty state titles, project card name, progress task |
| `TextAppearance.Material3.TitleSmall` | Values (author, timestamp, branch name, commit msg) |
| `TextAppearance.Material3.BodyLarge` | Crash details descriptions |
| `TextAppearance.Material3.BodyMedium` | SHA values, commit body, folder names, TextInputEditText |
| `TextAppearance.Material3.BodySmall` | Environment badge |
| `TextAppearance.Material3.LabelLarge` | Settings titles, empty editor text, tab text, radio buttons |
| `TextAppearance.Material3.LabelMedium` | Subtitles, descriptions, EditText inputs, search fields, button text |
| `TextAppearance.Material3.LabelSmall` | Section labels, field labels, helper text, primary action button text, badges |

### Text Properties

| Property | Values Used |
|----------|-------------|
| `letterSpacing` | `0.04` (console title, dialog headers), `0.06` (commit detail labels) |
| `lineSpacingMultiplier` | `1.2` (commit message), `1.4` (console logs) |
| `includeFontPadding` | `false` (app bar titles, editor project name) |
| `textAllCaps` | `true` (badges, labels, section headers) |
| `textIsSelectable` | `true` (console logs only) |

---

## 2. Color System

### Theme Mapping (Material3)

```
colorPrimary          → vcode_accent_primary   (Light: #2B6EE8, Dark: #4F8EF7)
colorSecondary        → vcode_accent_secondary (Light: #6340E0, Dark: #7C5CFC)
colorTertiary         → vcode_accent_success   (Light: #1FB870, Dark: #3DD68C)
colorError            → vcode_accent_error     (Light: #D93B38, Dark: #F25F5C)
colorBackground       → vcode_bg_deep          (Light: #F0F2F8, Dark: #0D0F14)
colorSurface          → vcode_bg_surface       (Light: #FFFFFF, Dark: #13161E)
colorSurfaceContainer → vcode_bg_card          (Light: #F7F8FC, Dark: #1C2030)
colorSurfaceContainerHigh → vcode_bg_elevated  (Light: #ECEEF6, Dark: #242840)
colorOnSurface        → vcode_text_primary     (Light: #0D0F1A, Dark: #E8EAF2)
colorOnSurfaceVariant → vcode_on_surface_variant (Light: #5A6080, Dark: #7B82A0)
```

### Surface Hierarchy

| Level | Attr | Light | Dark | Usage |
|-------|------|-------|------|-------|
| Lowest | `colorSurfaceContainerLowest` | `#F0F2F8` | `#0D0F14` | Activity backgrounds |
| Low | `colorSurfaceContainerLow` | `#FFFFFF` | `#13161E` | AppBarLayout |
| Base | `colorSurfaceContainer` | `#F7F8FC` | `#1C2030` | Cards, sections |
| High | `colorSurfaceContainerHigh` | `#ECEEF6` | `#242840` | Input backgrounds, search bars |

### Text Colors

| Token | Attr/Resource | Light | Dark | Usage |
|-------|---------------|-------|------|-------|
| Primary | `?attr/colorOnSurface` or `@color/vcode_text_primary` | `#0D0F1A` | `#E8EAF2` | Titles, values, input text |
| Secondary | `?attr/colorOnSurfaceVariant` or `@color/vcode_text_secondary` | `#5A6080` | `#7B82A0` | Descriptions, labels, hints, icons |
| Hint | `@color/vcode_text_hint` | `#9BA3C0` | `#3E4560` | Placeholder text, line numbers |
| Accent | `?attr/colorPrimary` or `@color/vcode_accent_primary` | `#2B6EE8` | `#4F8EF7` | Links, active states, SHAs |
| Error | `?attr/colorError` or `@color/vcode_accent_error` | `#D93B38` | `#F25F5C` | Error text, delete titles |

### Accent Colors

| Name | Light | Dark | Usage |
|------|-------|------|-------|
| `vcode_accent_primary` | `#2B6EE8` | `#4F8EF7` | Links, active states, progress |
| `vcode_accent_secondary` | `#6340E0` | `#7C5CFC` | Secondary accent |
| `vcode_accent_success` | `#1FB870` | `#3DD68C` | Success, tertiary, lock icon |
| `vcode_accent_warning` | `#D4850A` | `#F5A623` | Folder icons, dirty dots, modified |
| `vcode_accent_error` | `#D93B38` | `#F25F5C` | Errors, delete, clear actions |

### Icon Tinting Rules

| Color | When to Use |
|-------|-------------|
| `?attr/colorOnSurface` | Primary toolbar icons (back, menu, close) |
| `?attr/colorOnSurfaceVariant` | Secondary icons (undo, redo, save, overflow, chevrons) |
| `?attr/colorPrimary` | Search icon, folder icon, progress indicators |
| `?attr/colorTertiary` | Run button, JSON status valid |
| `?attr/colorOutlineVariant` | Empty state decorative icons |
| `?attr/colorError` | Delete/trash icons |
| `@color/vcode_text_secondary` | Neutral action icons (refresh, browser, branch) |
| `@color/vcode_accent_warning` | Folder icons in file tree |

### Divider & Stroke

| Resource | Light | Dark | Usage |
|----------|-------|------|-------|
| `@color/vcode_divider` | `#DDE0EC` | `#1E2235` | Card strokes, separator lines, toggle borders |
| `?attr/colorOutline` | `#DDE0EC` | `#1E2235` | Cancel button stroke |
| `?attr/colorPrimary` | — | — | Active template stroke, tab indicator |
| `?attr/colorError` | — | — | Destructive button stroke |

---

## 3. Dimensions

### Named Dimension Resources

| Resource | Value | Usage |
|----------|-------|-------|
| `vcode_app_toolbar_height` | `65dp` | Activity toolbar height |
| `vcode_app_gap_horizontal` | `24dp` | Universal page horizontal margin |
| `vcode_editor_toolbar_height` | `56dp` | Editor action toolbar |
| `vcode_editor_tab_bar_height` | `44dp` | Tab bar height |
| `vcode_editor_tab_min_width` | `100dp` | Tab minimum width |
| `vcode_editor_tab_max_width` | `180dp` | Tab maximum width |
| `vcode_file_drawer_width` | `320dp` | File tree drawer width |
| `vcode_file_tree_item_height` | `40dp` | File tree row height |
| `vcode_file_tree_indent_per_level` | `16dp` | Tree indentation per depth |
| `vcode_breadcrumb_height` | `32dp` | Breadcrumb bar |
| `vcode_json_status_bar_height` | `32dp` | JSON status bar |
| `vcode_card_corner_radius` | `12dp` | Standard card corners |
| `vcode_button_corner_radius` | `8dp` | Button corners |
| `vcode_dialog_corner_radius` | `16dp` | Dialog corners |
| `vcode_empty_state_icon_size` | `80dp` | Empty state icons |
| `vcode_project_card_height` | `180dp` | Project card height |
| `vcode_git_status_icon_size` | `8dp` | Small status dot |
| `vcode_tab_close_button_size` | `16dp` | Tab X button |
| `vcode_tab_dirty_dot_size` | `6dp` | Unsaved indicator |
| `vcode_autocomplete_popup_max_height` | `240dp` | Autocomplete dropdown |
| `vcode_autocomplete_popup_width` | `280dp` | Autocomplete width |
| `vcode_autocomplete_item_height` | `48dp` | Autocomplete row |
| `vcode_diff_line_number_width` | `40dp` | Diff gutter |
| `vcode_commit_avatar_size` | `32dp` | Commit author avatar |
| `vcode_branch_chip_height` | `28dp` | Branch chip |

### Shape System

| Shape Level | Corner Radius | Usage |
|-------------|---------------|-------|
| Small | `8dp` | Buttons, input fields |
| Medium | `12dp` | Cards, bottom sheets, popups |
| Large | `16dp` | Dialogs |

### Corner Radius Values Used

| Radius | Usage |
|--------|-------|
| `0dp` | Console card (flat top) |
| `4dp` | Find toggle buttons |
| `8dp` | Reset confirm card, snippet card, buttons |
| `12dp` | Settings sections, cards, popups, template cards |
| `14dp` | Commit details card |
| `16dp` | Dialogs |

### Elevation

| Value | Usage |
|-------|-------|
| `0dp` | Most cards (flat Material3 style) |
| `4dp` | Custom popup, find/replace bar |
| `8dp` | Debug bottom bar, console card |

---

## 4. Spacing System

### Scale

| Token | dp | Usage |
|-------|-----|-------|
| 2xs | `2` | Label-to-value tight gap |
| xs | `4` | Minimal gaps, chip spacing, helper text margin |
| sm | `6` | Label-to-input gap, progress details gap |
| base | `8` | Standard small gap, icon margins, search padding |
| md | `12` | List item padding, section margins, tab padding |
| lg | `16` | Card internal padding, horizontal page padding, toolbar margin |
| xl | `20` | Search bar spacing, between-section gaps |
| 2xl | `24` | Field group spacing, FAB margin, section headers (`= vcode_app_gap_horizontal`) |
| 3xl | `32` | Empty state padding, large gaps before actions |
| 4xl | `40` | Bottom sheet action button margin-top |
| 5xl | `48` | Bottom sheet bottom padding |
| 6xl | `88` | RecyclerView bottom padding (FAB clearance) |

### Padding Rules

| Context | Value |
|---------|-------|
| Page horizontal margin | `@dimen/vcode_app_gap_horizontal` (24dp) |
| EditText internal padding | `14dp` |
| Card content padding | `16dp` |
| Search bar padding | `8dp` |
| Icon button padding | `6-12dp` (size dependent) |
| Button horizontal padding | `24dp` |
| Bottom sheet menu item horizontal | `20dp` |

### Margin Rules

| Context | Value |
|---------|-------|
| Label above input | `marginTop="24dp"` (label), `marginTop="6dp"` (input below label) |
| Between cards/sections | `16dp` |
| Action button from content | `marginTop="40dp"` |
| Bottom sheet dismiss margin | `marginBottom="20dp"` |
| Empty state description to button | `marginBottom="32dp"` |
| FAB from edges | `24dp` |

---

## 5. Components & Widgets

### Layout Containers

| Widget | Usage |
|--------|-------|
| `LinearLayout` | Primary container (activities, bottom sheets, list items) |
| `ConstraintLayout` | Complex layouts needing constraints (activities, cards) |
| `FrameLayout` | Swipeable cards (overlay pattern), single-child containers |
| `CoordinatorLayout` | Fragment with AppBar + scrolling content |
| `DrawerLayout` | Editor (file tree drawer) |
| `RelativeLayout` | Git activity (toolbar positioning) |
| `NestedScrollView` | Scrollable bottom sheet/fragment content |
| `HorizontalScrollView` | Template rows, breadcrumbs |

### Material Components

| Widget | Usage |
|--------|-------|
| `MaterialCardView` | Cards, dialog roots |
| `MaterialButton` | All buttons (filled, outlined, text) |
| `MaterialSwitch` | Settings toggles (scaled 0.8x or 0.75x) |
| `BottomSheetDragHandleView` | Bottom sheet drag indicator |
| `TabLayout` | Git tab navigation |
| `FloatingActionButton` | Primary FAB (projects) |
| `ExtendedFloatingActionButton` | Empty state action |
| `TextInputLayout` | Material outlined inputs (sparingly) |
| `CircularProgressIndicator` | Loading states |
| `LinearProgressIndicator` | Clone/operation progress |
| `MaterialRadioButton` | Selection options |

### Custom Views

| Class | Usage |
|-------|-------|
| `com.cocode.vcode.ide.views.TabBar` | Editor tab strip |
| `com.cocode.vcode.ide.views.BreadcrumbView` | Path breadcrumb navigation |
| `com.cocode.vcode.ide.views.JsonStatusBar` | JSON validation status |
| `com.cocode.vcode.ide.views.FindReplaceBar` | Find & replace panel |
| `com.cocode.vcode.ide.views.GitStatusBadge` | Git status indicator dot |

---

## 6. Layout Structure Templates

### Activity Root

```xml
<!-- Option A: ConstraintLayout (complex activities) -->
<ConstraintLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/colorSurfaceContainerLowest"
    android:fitsSystemWindows="true">

<!-- Option B: LinearLayout (simple vertical stack) -->
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/colorSurfaceContainerLowest"
    android:fitsSystemWindows="true"
    android:orientation="vertical">
```

### Toolbar

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="@dimen/vcode_app_toolbar_height"
    android:background="?attr/colorSurfaceContainerLow"
    android:gravity="center_vertical"
    android:paddingHorizontal="@dimen/vcode_app_gap_horizontal">

    <ImageView
        android:layout_width="28dp"
        android:layout_height="28dp"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:importantForAccessibility="no"
        android:padding="3dp"
        android:src="@drawable/ic_chevron_right"
        android:rotation="180"
        app:tint="?attr/colorOnSurface" />

    <TextView
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="8dp"
        android:layout_weight="1"
        android:includeFontPadding="false"
        android:textAppearance="@style/TextAppearance.Material3.ActionBar.Title"
        android:textColor="?attr/colorOnSurface" />
</LinearLayout>
```

### Bottom Sheet

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@drawable/bottom_sheet_background"
    android:orientation="vertical">

    <com.google.android.material.bottomsheet.BottomSheetDragHandleView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center_horizontal" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="@dimen/vcode_app_gap_horizontal"
        android:textAppearance="@style/TextAppearance.Material3.TitleLarge"
        android:textColor="?attr/colorOnSurface" />

    <!-- Optional subtitle -->
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="@dimen/vcode_app_gap_horizontal"
        android:layout_marginTop="4dp"
        android:textAppearance="@style/TextAppearance.Material3.LabelMedium"
        android:textColor="?attr/colorOnSurfaceVariant" />

    <!-- Field label -->
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="@dimen/vcode_app_gap_horizontal"
        android:layout_marginTop="24dp"
        android:textAppearance="@style/TextAppearance.Material3.LabelSmall"
        android:textColor="?attr/colorOnSurfaceVariant" />

    <!-- Input field -->
    <EditText
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginHorizontal="@dimen/vcode_app_gap_horizontal"
        android:layout_marginTop="6dp"
        android:background="?attr/colorSurfaceContainerHigh"
        android:imeOptions="actionDone"
        android:importantForAutofill="no"
        android:inputType="text"
        android:maxLines="1"
        android:padding="14dp"
        android:textAppearance="@style/TextAppearance.Material3.LabelMedium"
        android:textColor="?attr/colorOnSurface"
        android:textColorHint="?attr/colorOnSurfaceVariant" />

    <!-- Primary action button -->
    <com.google.android.material.button.MaterialButton
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="end"
        android:layout_marginTop="40dp"
        android:layout_marginEnd="@dimen/vcode_app_gap_horizontal"
        android:layout_marginBottom="20dp"
        android:paddingHorizontal="24dp"
        android:textAppearance="@style/TextAppearance.Material3.LabelSmall"
        android:textColor="?attr/colorOnPrimary"
        app:backgroundTint="?attr/colorPrimary" />
</LinearLayout>
```

### Card Section (Settings-Style)

```xml
<com.google.android.material.card.MaterialCardView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginHorizontal="@dimen/vcode_app_gap_horizontal"
    android:layout_marginTop="12dp"
    app:cardBackgroundColor="?attr/colorSurfaceContainer"
    app:cardCornerRadius="12dp"
    app:cardElevation="0dp"
    app:strokeWidth="0dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingVertical="4dp">
        <!-- Settings rows go here -->
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

### Settings Row

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="?attr/selectableItemBackground"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:paddingHorizontal="16dp"
    android:paddingVertical="12dp">

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:textAppearance="@style/TextAppearance.Material3.LabelLarge"
            android:textColor="?attr/colorOnBackground" />

        <TextView
            android:layout_marginTop="2dp"
            android:textAppearance="@style/TextAppearance.Material3.LabelMedium"
            android:textColor="?attr/colorOnSurfaceVariant" />
    </LinearLayout>

    <com.google.android.material.materialswitch.MaterialSwitch
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:scaleX="0.8"
        android:scaleY="0.8" />
</LinearLayout>
```

### List Item (RecyclerView)

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="?attr/selectableItemBackground"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:paddingHorizontal="16dp"
    android:paddingVertical="10dp">

    <ImageView
        android:layout_width="20dp"
        android:layout_height="20dp"
        android:importantForAccessibility="no"
        app:tint="?attr/colorOnSurfaceVariant" />

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="12dp"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:textAppearance="@style/TextAppearance.Material3.TitleSmall"
            android:textColor="?attr/colorOnSurface" />

        <TextView
            android:layout_marginTop="2dp"
            android:textAppearance="@style/TextAppearance.Material3.LabelSmall"
            android:textColor="@color/vcode_text_secondary" />
    </LinearLayout>

    <ImageView
        android:layout_width="28dp"
        android:layout_height="28dp"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:importantForAccessibility="no"
        android:padding="4dp"
        app:tint="?attr/colorOnSurfaceVariant" />
</LinearLayout>
```

### Empty State

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:gravity="center"
    android:orientation="vertical"
    android:padding="32dp"
    android:visibility="gone"
    tools:visibility="visible">

    <ImageView
        android:layout_width="@dimen/vcode_empty_state_icon_size"
        android:layout_height="@dimen/vcode_empty_state_icon_size"
        android:importantForAccessibility="no"
        app:tint="?attr/colorOutlineVariant" />

    <TextView
        android:layout_marginTop="24dp"
        android:textAppearance="@style/TextAppearance.Material3.TitleMedium"
        android:textColor="?attr/colorOnSurface" />

    <TextView
        android:layout_marginTop="8dp"
        android:gravity="center"
        android:textAppearance="@style/TextAppearance.Material3.LabelMedium"
        android:textColor="?attr/colorOnSurfaceVariant" />

    <!-- Optional action button -->
    <com.google.android.material.button.MaterialButton
        android:layout_marginTop="32dp"
        android:paddingHorizontal="24dp"
        android:textAppearance="@style/TextAppearance.Material3.LabelSmall"
        android:textColor="?attr/colorOnPrimary"
        app:backgroundTint="?attr/colorPrimary" />
</LinearLayout>
```

### Swipeable Card (Left-to-Reveal Actions)

```xml
<FrameLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content">

    <!-- Action layer (behind) -->
    <LinearLayout
        android:id="@+id/layout_actions"
        android:layout_width="wrap_content"
        android:layout_height="match_parent"
        android:layout_gravity="end|center_vertical"
        android:orientation="horizontal">

        <ImageView
            android:layout_width="44dp"
            android:layout_height="44dp"
            android:padding="10dp"
            app:tint="?attr/colorOnPrimary" />
    </LinearLayout>

    <!-- Foreground card (translates on swipe) -->
    <MaterialCardView ...>
        <!-- Card content -->
    </MaterialCardView>
</FrameLayout>
```

---

## 7. Button Patterns

### Primary Filled Button

```xml
<com.google.android.material.button.MaterialButton
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:paddingHorizontal="24dp"
    android:textAppearance="@style/TextAppearance.Material3.LabelSmall"
    android:textColor="?attr/colorOnPrimary"
    app:backgroundTint="?attr/colorPrimary" />
```

### Destructive Filled Button

```xml
<com.google.android.material.button.MaterialButton
    android:textColor="?attr/colorOnError"
    app:backgroundTint="?attr/colorError" />
```

### Outlined Button

```xml
<com.google.android.material.button.MaterialButton
    style="@style/Widget.Material3.Button.OutlinedButton"
    android:textColor="?attr/colorOnSurface"
    app:strokeColor="?attr/colorOutline" />
```

### Destructive Outlined Button

```xml
<com.google.android.material.button.MaterialButton
    style="@style/Widget.Material3.Button.OutlinedButton"
    android:textColor="?attr/colorError"
    app:strokeColor="?attr/colorError" />
```

### Text Button

```xml
<com.google.android.material.button.MaterialButton
    style="@style/Widget.Material3.Button.TextButton"
    android:textColor="?attr/colorPrimary" />
```

### Button Pair (50/50 Width)

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:orientation="horizontal">

    <com.google.android.material.button.MaterialButton
        style="@style/Widget.Material3.Button.OutlinedButton"
        android:layout_width="0dp"
        android:layout_weight="1"
        android:layout_marginEnd="8dp" />

    <com.google.android.material.button.MaterialButton
        android:layout_width="0dp"
        android:layout_weight="1"
        android:layout_marginStart="8dp" />
</LinearLayout>
```

### Compact Toggle Button (Find/Replace)

```xml
<com.google.android.material.button.MaterialButton
    style="@style/Widget.Material3.Button.OutlinedButton"
    android:layout_height="32dp"
    android:insetTop="0dp"
    android:insetBottom="0dp"
    android:minWidth="0dp"
    android:paddingHorizontal="12dp"
    android:textSize="12sp"
    app:cornerRadius="4dp"
    app:strokeColor="@color/vcode_divider" />
```

---

## 8. Input Field Patterns

### Standard EditText (Flat Background)

```xml
<EditText
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginHorizontal="@dimen/vcode_app_gap_horizontal"
    android:layout_marginTop="6dp"
    android:background="?attr/colorSurfaceContainerHigh"
    android:hint="Placeholder text"
    android:imeOptions="actionDone"
    android:importantForAutofill="no"
    android:inputType="text"
    android:maxLines="1"
    android:padding="14dp"
    android:textAppearance="@style/TextAppearance.Material3.LabelMedium"
    android:textColor="?attr/colorOnSurface"
    android:textColorHint="?attr/colorOnSurfaceVariant" />
```

### Multiline Code Input

```xml
<EditText
    android:minHeight="110dp"
    android:gravity="top"
    android:inputType="textMultiLine"
    android:fontFamily="monospace" />
```

### TextInputLayout (Material Outlined — Sparingly)

```xml
<com.google.android.material.textfield.TextInputLayout
    style="@style/Widget.Material3.TextInputLayout.OutlinedBox"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:hint="Label"
    app:errorEnabled="true">

    <com.google.android.material.textfield.TextInputEditText
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="text"
        android:padding="14dp"
        android:textAppearance="@style/TextAppearance.Material3.BodyMedium" />
</com.google.android.material.textfield.TextInputLayout>
```

### Inline Search Bar

```xml
<LinearLayout
    android:background="?attr/colorSurfaceContainerHigh"
    android:gravity="center_vertical"
    android:padding="8dp">

    <ImageView
        android:layout_width="28dp"
        android:layout_height="28dp"
        android:padding="5dp"
        android:src="@drawable/ic_magnifying_glass"
        app:tint="?attr/colorPrimary" />

    <EditText
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:background="@null"
        android:hint="Search..."
        android:inputType="text"
        android:textAppearance="@style/TextAppearance.Material3.LabelMedium"
        android:textColor="?attr/colorOnSurface"
        android:textColorHint="?attr/colorOnSurfaceVariant" />
</LinearLayout>
```

### Field Label + Input Pattern

```xml
<!-- Label -->
<TextView
    android:layout_marginStart="@dimen/vcode_app_gap_horizontal"
    android:layout_marginTop="24dp"
    android:text="FIELD NAME"
    android:textAppearance="@style/TextAppearance.Material3.LabelSmall"
    android:textColor="?attr/colorOnSurfaceVariant" />

<!-- Input (6dp below label) -->
<EditText
    android:layout_marginTop="6dp"
    ... />
```

---

## 9. RecyclerView Configuration

```xml
<androidx.recyclerview.widget.RecyclerView
    android:layout_width="match_parent"
    android:layout_height="0dp"
    android:layout_weight="1"
    android:clipToPadding="false"
    android:paddingBottom="24dp"
    android:scrollbars="none" />
```

### Key Rules
- Always set `clipToPadding="false"` when bottom padding exists
- Set `nestedScrollingEnabled="false"` when inside NestedScrollView
- Use `paddingBottom="88dp"` when FAB is present
- Use `layout_weight="1"` with `height="0dp"` to fill remaining space
- Always `scrollbars="none"`

---

## 10. Naming Conventions

### ID Prefixes

| Prefix | Widget |
|--------|--------|
| `tv_` | TextView |
| `et_` | EditText |
| `btn_` | Clickable ImageView / Button |
| `iv_` | Decorative ImageView |
| `rv_` | RecyclerView |
| `layout_` | Container LinearLayout/FrameLayout |
| `progress_` | Progress indicators |
| `switch_` | MaterialSwitch |
| `card_` | MaterialCardView |
| `fab_` | FloatingActionButton |
| `op_` | Settings option row |
| `section_` | Settings section card |
| `til_` | TextInputLayout |
| `rg_` | RadioGroup |
| `rb_` / `radio_` | RadioButton |

### File Naming

| Prefix | Category |
|--------|----------|
| `activity_` | Activity layouts |
| `bottom_sheet_` | Bottom sheet dialogs |
| `dialog_` | Alert dialogs |
| `fragment_` | Fragment layouts |
| `item_` | RecyclerView item layouts |
| `layout_` | Reusable sub-layouts |
| `view_` | Custom compound views |

### Drawable Naming

| Prefix | Category |
|--------|----------|
| `ic_` | Icons |
| `vcode_bg_` | Background drawables |
| `vcode_ripple_` | Ripple effects |
| `bottom_sheet_background` | Bottom sheet bg (no prefix) |

### Color Naming

All color resources use `vcode_` prefix:
- `vcode_bg_*` — background colors
- `vcode_accent_*` — accent/brand colors
- `vcode_text_*` — text colors
- `vcode_on_*` — on-container colors
- `vcode_*_container` — container colors
- `vcode_divider` — divider/border
- `vcode_diff_*` — diff viewer colors
- `vcode_git_*` — git status colors
- `vcode_lang_*` — language badge colors
- `vcode_file_*` — file type colors

---

## 11. Drawables & Backgrounds

### Background Treatments

| Drawable | Usage |
|----------|-------|
| `@drawable/bottom_sheet_background` | All bottom sheets (rounded top corners) |
| `@drawable/vcode_bg_toolbar` | Editor toolbar, find/replace bar |
| `@drawable/vcode_bg_symbol_key` | Badges, URL bar, language tags |
| `@drawable/vcode_bg_symbol_key_interactive` | Font size +/- buttons |
| `@drawable/vcode_bg_console_badge` | Console count badge |
| `@drawable/vcode_bg_console_log_area` | Console scroll container |
| `@drawable/vcode_bg_json_status_valid` | JSON valid status bar |
| `@drawable/vcode_bg_git_status_modified` | Dirty dot indicator |
| `@drawable/vcode_git_node_outline` | Timeline node circle |
| `@drawable/vcode_ripple_tab` | Editor tab ripple |

### Ripple Patterns

| Pattern | Usage |
|---------|-------|
| `?attr/selectableItemBackground` | List items, settings rows, clickable containers |
| `?attr/selectableItemBackgroundBorderless` | Icon buttons (all standalone ImageViews) |
| `@drawable/vcode_ripple_tab` | Editor tabs |

---

## 12. Icon Sizes Reference

| Size | Usage |
|------|-------|
| `14dp` | Inline small icons (lock, JSON status) |
| `16dp` | Chevrons, terminal icon |
| `18dp` | File tree file type icons |
| `20dp` | List item icons, branch icon, check mark |
| `22dp` | Autocomplete badge, file icon in git, menu item icon |
| `24dp` | Folder icon, warning icon |
| `28dp` | Toolbar icons (standard), search icon, overflow |
| `30dp` | Template card icon, snippet action icons |
| `32dp` | Console buttons, find nav, font buttons |
| `36dp` | Editor toolbar buttons |
| `40dp` | Menu button, preview toolbar buttons, delete icon |
| `44dp` | Swipe action reveal buttons |
| `48dp` | Back button (large), error icon |
| `50dp` | Floating circular action buttons |
| `80dp` | Empty state icons (`@dimen/vcode_empty_state_icon_size`) |

### ImageView Icon Button Pattern

```xml
<ImageView
    android:layout_width="28dp"
    android:layout_height="28dp"
    android:background="?attr/selectableItemBackgroundBorderless"
    android:clickable="true"
    android:focusable="true"
    android:importantForAccessibility="no"
    android:padding="4dp"
    android:src="@drawable/ic_*"
    app:tint="?attr/colorOnSurfaceVariant" />
```

---

## 13. Visibility & State Patterns

### Default Visibility

| State | Elements |
|-------|----------|
| `gone` | Progress indicators, error layouts, empty states, optional panels (console, find bar, JSON status), badges, secondary icons |
| `visible` | Main content, primary UI elements |

### Alpha for De-emphasis

| Alpha | Usage |
|-------|-------|
| `0.3` | Divider lines |
| `0.5` | Timeline connector lines |
| `0.6` | De-emphasized icons (credentials arrow) |

### Switch Scaling

| Scale | Context |
|-------|---------|
| `0.8` | Settings page switches |
| `0.75` | Popup menu switches (more compact) |

### Rotation

| Value | Usage |
|-------|-------|
| `180°` | `ic_chevron_right` rotated to act as back/left arrow |

---

## 14. Accessibility

### Rules

1. **All decorative ImageViews:** Set `android:importantForAccessibility="no"`
2. **All EditText fields:** Set `android:importantForAutofill="no"`
3. **All clickable views:** Set `android:clickable="true"` and `android:focusable="true"`
4. **Labels for inputs:** Use `android:labelFor="@id/..."` on associated TextView
5. **Content descriptions:** Use `android:contentDescription="@string/..."` only for actionable icons that convey meaning (e.g., tab close button)
6. **Touch targets:** Minimum 28dp for icon buttons (with padding extending the touch area via `selectableItemBackgroundBorderless`)

---

## 15. Quick Reference Checklist

When creating a new layout:

- [ ] Root background: `?attr/colorSurfaceContainerLowest` (activity) or `@drawable/bottom_sheet_background` (bottom sheet)
- [ ] Page margins: `@dimen/vcode_app_gap_horizontal` (24dp)
- [ ] Toolbar height: `@dimen/vcode_app_toolbar_height` (65dp)
- [ ] Title style: `TextAppearance.Material3.ActionBar.Title` (toolbar) or `TitleLarge` (bottom sheet)
- [ ] Body text: `TextAppearance.Material3.LabelMedium`, color `?attr/colorOnSurfaceVariant`
- [ ] Cards: `colorSurfaceContainer`, cornerRadius=12dp, elevation=0dp, strokeWidth=0dp
- [ ] Inputs: background=`?attr/colorSurfaceContainerHigh`, padding=14dp, marginTop=6dp from label
- [ ] Buttons: textAppearance=LabelSmall, paddingHorizontal=24dp
- [ ] Icons: `selectableItemBackgroundBorderless`, `importantForAccessibility="no"`, `app:tint`
- [ ] Field label margin-top: 24dp; input margin-top from label: 6dp
- [ ] Action button: `marginTop="40dp"`, `marginBottom="20dp"`, `layout_gravity="end"`
- [ ] Empty states: 80dp icon, TitleMedium + LabelMedium, center gravity
- [ ] All IDs: `snake_case` with appropriate widget prefix
- [ ] All colors: Use `?attr/` theme attributes or `@color/vcode_*` resources — never hardcode hex
- [ ] All dimens: Use `@dimen/vcode_*` for standard sizes — hardcode only one-off values
