import { memo } from 'react';

/**
 * PageHeader — page title (h1, Archivo display cut) with optional subtitle, badge and actions.
 * API kept stable: title | subtitle | description | badge | action.
 */
export const PageHeader = memo(function PageHeader({ title, subtitle, description, badge, action }) {
  const sub = subtitle || description;
  return (
    <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2.5">
          <h1 className="app-display text-[1.625rem] leading-tight text-on-surface [text-wrap:balance]">
            {title}
          </h1>
          {badge}
        </div>
        {sub && <p className="mt-1.5 max-w-[68ch] text-sm text-text-secondary">{sub}</p>}
      </div>
      {action && <div className="flex shrink-0 flex-wrap items-center gap-2">{action}</div>}
    </div>
  );
});
