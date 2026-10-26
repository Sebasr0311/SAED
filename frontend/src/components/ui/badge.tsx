import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@/lib/utils"

const badgeVariants = cva(
  "inline-flex items-center gap-1 rounded-[3px] border px-2 py-0.5 text-xs font-bold leading-[1.35] transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2",
  {
    variants: {
      variant: {
        default:
          "border-transparent bg-btn-primary text-white",
        secondary:
          "border-border bg-surface-dim text-on-surface",
        destructive:
          "border-tint-red-fg/25 bg-tint-red-bg text-tint-red-fg",
        outline:
          "border-border bg-transparent text-on-surface",
        success:
          "border-tint-green-fg/25 bg-tint-green-bg text-tint-green-fg",
        warning:
          "border-tint-amber-fg/25 bg-tint-amber-bg text-tint-amber-fg",
        info:
          "border-tint-blue-fg/25 bg-tint-blue-bg text-tint-blue-fg",
        lavender:
          "border-tint-purple-fg/25 bg-tint-purple-bg text-tint-purple-fg",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
)

export interface BadgeProps
  extends React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof badgeVariants> {}

function Badge({ className, variant, ...props }: BadgeProps) {
  return (
    <div className={cn(badgeVariants({ variant }), className)} {...props} />
  )
}

export { Badge, badgeVariants }
