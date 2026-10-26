import * as React from "react"

import { cn } from "@/lib/utils"

const Input = React.forwardRef<HTMLInputElement, React.ComponentProps<"input">>(
  ({ className, type, ...props }, ref) => {
    return (
      <input
        type={type}
        className={cn(
          "flex h-9 w-full rounded-md border border-input bg-surface text-on-surface px-3 py-1 text-base shadow-xs file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-foreground placeholder:text-text-hint transition-[border-color,box-shadow] duration-150 hover:border-keyline/70 focus-visible:outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/25 disabled:cursor-not-allowed disabled:opacity-50 md:text-sm aria-invalid:!border-destructive aria-invalid:!ring-destructive/30 aria-invalid:focus-visible:!ring-destructive aria-invalid:bg-destructive/5",
          className
        )}
        ref={ref}
        {...props}
      />
    )
  }
)
Input.displayName = "Input"

export { Input }
