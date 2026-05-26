# Icon System

Two icon sources are used in this app. The rule is simple:

## `lucide-react` — nav, chrome, generic actions

Use lucide for UI chrome: navigation, form controls, generic actions.

```tsx
import { Search, Settings, ChevronDown, Trash } from 'lucide-react';
```

Examples: `Home`, `Users`, `Database`, `Settings`, `LogOut`, `X`, `Plus`, `Calendar`, `Eye`.

## `@/components/icons` — metric + brand glyphs

Use the custom icon library for the 22 metric icons and brand-specific glyphs.
These are purpose-drawn at the 16px grid with an editorial feel.

```tsx
import { Commits, PRMerged, LeadTime, Focus, Jira, Folder } from '@/components/icons';
```

Full list: `Commits`, `PRMerged`, `PRCreated`, `LeadTime`, `Focus`, `Review`,
`FirstCommit`, `IssuesCreated`, `IssuesClosed`, `IssueLead`, `Churn`, `DeepWork`,
`AfterHours`, `Refactor`, `NoReview`, `MergeFreq`, `Silo`, `PRSize`, `AI`,
`Branch`, `Jira`, `Folder`.

All icons share the same props as `React.SVGProps<SVGSVGElement>`.
