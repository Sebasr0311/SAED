import { cn } from '../../lib/utils.js';
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from './dialog.tsx';

/**
 * Wrapper de compatibilidad sobre el Dialog de shadcn/ui.
 * Mantiene la API del kit anterior (open|onClose|title|footer|size).
 */
export function Modal({ open, onClose, title, children, footer, size = 'md' }) {
  const sizes = {
    sm: 'max-w-md',
    md: 'max-w-lg',
    lg: 'max-w-2xl',
    xl: 'max-w-4xl',
  };
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose?.()}>
      <DialogContent className={cn('max-h-[90dvh] overflow-y-auto', sizes[size] || sizes.md)}>
        <DialogHeader>
          <DialogTitle className="text-lg font-semibold">{title}</DialogTitle>
        </DialogHeader>
        {children}
        {footer && <DialogFooter>{footer}</DialogFooter>}
      </DialogContent>
    </Dialog>
  );
}
