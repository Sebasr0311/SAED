import { memo, forwardRef } from 'react';
import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';
import { cn } from '../../lib/utils.js';

export const Breadcrumb = forwardRef(function Breadcrumb({ className, ...props }, ref) {
  return <nav ref={ref} aria-label="breadcrumb" className={cn('flex', className)} {...props} />;
});
Breadcrumb.displayName = 'Breadcrumb';

export const BreadcrumbList = forwardRef(function BreadcrumbList({ className, ...props }, ref) {
  return (
    <ol
      ref={ref}
      className={cn(
        'flex flex-wrap items-center gap-1.5 break-words text-sm text-muted-foreground sm:gap-2.5',
        className
      )}
      {...props}
    />
  );
});
BreadcrumbList.displayName = 'BreadcrumbList';

export const BreadcrumbItem = forwardRef(function BreadcrumbItem({ className, ...props }, ref) {
  return <li ref={ref} className={cn('inline-flex items-center gap-1.5', className)} {...props} />;
});
BreadcrumbItem.displayName = 'BreadcrumbItem';

export const BreadcrumbLink = forwardRef(function BreadcrumbLink({ className, href, to, ...props }, ref) {
  const target = to || href;
  if (target) {
    return (
      <Link
        ref={ref}
        to={target}
        className={cn('transition-colors hover:text-foreground', className)}
        {...props}
      />
    );
  }
  return (
    <span
      ref={ref}
      className={cn('transition-colors hover:text-foreground', className)}
      {...props}
    />
  );
});
BreadcrumbLink.displayName = 'BreadcrumbLink';

export const BreadcrumbPage = forwardRef(function BreadcrumbPage({ className, ...props }, ref) {
  return (
    <span
      ref={ref}
      role="link"
      aria-disabled="true"
      aria-current="page"
      className={cn('font-medium text-foreground', className)}
      {...props}
    />
  );
});
BreadcrumbPage.displayName = 'BreadcrumbPage';

export const BreadcrumbSeparator = memo(function BreadcrumbSeparator({ children, className, ...props }) {
  return (
    <li
      role="presentation"
      aria-hidden="true"
      className={cn('inline-flex items-center text-muted-foreground/60', className)}
      {...props}
    >
      {children ?? <ChevronRight className="h-3.5 w-3.5" aria-hidden="true" />}
    </li>
  );
});
BreadcrumbSeparator.displayName = 'BreadcrumbSeparator';

/**
 * BreadcrumbNav — Componente de navegación jerárquica con soporte accesible y breadcrumbs anidados.
 * Uso directo con array de items o composable mediante las primitivas exportadas.
 */
export const BreadcrumbNav = memo(function BreadcrumbNav({ items = [], className = '' }) {
  if (!items || items.length === 0) return null;

  return (
    <Breadcrumb className={className}>
      <BreadcrumbList>
        {items.map((item, index) => {
          const isLast = index === items.length - 1;
          return (
            <span key={item.label || index} className="inline-flex items-center gap-1.5 sm:gap-2.5">
              <BreadcrumbItem>
                {isLast ? (
                  <BreadcrumbPage>{item.label}</BreadcrumbPage>
                ) : (
                  <BreadcrumbLink to={item.href || item.to}>{item.label}</BreadcrumbLink>
                )}
              </BreadcrumbItem>
              {!isLast && <BreadcrumbSeparator />}
            </span>
          );
        })}
      </BreadcrumbList>
    </Breadcrumb>
  );
});
BreadcrumbNav.displayName = 'BreadcrumbNav';

export default BreadcrumbNav;
