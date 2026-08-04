# SuperDesign Extractable Components

## Layout Components

### AppLayout

- Source: `frontend/src/layouts/AppLayout.vue`
- Category: layout
- Description: Authenticated application shell with desktop header, mobile bottom navigation, account menu, and content outlet.
- Extractable props: `activeRoute` (string, default: "dashboard"), `displayName` (string), `showProfileRecommendation` (boolean, default: false), `mobileMenuOpen` (boolean, default: false)
- Hardcoded: Hiresemble navigation labels, icon names, layout CSS, logo treatment, account menu structure.

### PublicLayout

- Source: `frontend/src/layouts/PublicLayout.vue`
- Category: layout
- Description: Public authentication shell with brand context panel and form content outlet.
- Extractable props: `mode` (string, default: "login")
- Hardcoded: brand artwork, product copy, decorative SVG/CSS, layout breakpoints.

### JobDetailLayout

- Source: `frontend/src/layouts/JobDetailLayout.vue`
- Category: layout
- Description: Job workspace header with company/title context, status badge, horizontal workflow tabs, and nested content outlet.
- Extractable props: `activeTab` (string, default: "analysis"), `companyName` (string), `positionTitle` (string), `postingStatus` (string)
- Hardcoded: tab labels and order, icon names, workspace CSS.

## Basic Components

### BrandMark

- Source: `frontend/src/shared/ui/BrandMark.vue`
- Category: basic
- Description: Hiresemble logo mark and optional wordmark.
- Extractable props: `compact` (boolean, default: false), `inverse` (boolean, default: false)
- Hardcoded: logo asset, Hiresemble brand name, mark geometry.

### AppIcon

- Source: `frontend/src/shared/ui/AppIcon.vue`
- Category: basic
- Description: Shared inline SVG icon renderer.
- Extractable props: `name` (string), `size` (number, default: 20), `strokeWidth` (number, default: 1.8)
- Hardcoded: SVG path registry, currentColor behavior.

### PageHeader

- Source: `frontend/src/shared/ui/PageHeader.vue`
- Category: basic
- Description: Shared title, description and actions alignment.
- Extractable props: `eyebrow` (string), `title` (string), `description` (string), `variant` (string, default: "default")
- Hardcoded: slot positions and layout CSS class names.

### StatusBadge

- Source: `frontend/src/shared/ui/StatusBadge.vue`
- Category: basic
- Description: Compact semantic status label.
- Extractable props: `label` (string), `prefix` (string), `tone` (string, default: "neutral")
- Hardcoded: tone class contract.

### StatePanel

- Source: `frontend/src/shared/ui/StatePanel.vue`
- Category: basic
- Description: Loading, error and empty-state guidance with optional action.
- Extractable props: `title` (string), `description` (string), `icon` (string, default: "info"), `tone` (string, default: "neutral"), `actionLabel` (string)
- Hardcoded: panel hierarchy and icon placement.

### PaginationNav

- Source: `frontend/src/shared/ui/PaginationNav.vue`
- Category: basic
- Description: Previous/current/next page navigation.
- Extractable props: `page` (number), `totalPages` (number), `disabled` (boolean, default: false)
- Hardcoded: Korean previous/next labels and control order.

### AppNotifications

- Source: `frontend/src/shared/ui/AppNotifications.vue`
- Category: basic
- Description: Global toast/notification viewport.
- Extractable props: `notificationCount` (number, default: 0)
- Hardcoded: notification placement, transition behavior, dismissal control.

