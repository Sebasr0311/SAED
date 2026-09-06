import { useEffect, useRef } from 'react';
import { animate, createTimeline, stagger } from 'animejs';

/**
 * Hook to trigger an Anime.js animation when an element enters the viewport.
 */
export function useScrollReveal(options = {}) {
  const ref = useRef(null);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            const targets = options.selector
              ? el.querySelectorAll(options.selector)
              : el;

            animate(targets, {
              opacity: [0, 1],
              translateY: [options.distance || 28, 0],
              duration: options.duration || 700,
              delay: options.stagger ? stagger(options.stagger, { start: options.delay || 0 }) : (options.delay || 0),
              ease: options.ease || 'outExpo',
            });

            if (options.once !== false) {
              observer.unobserve(el);
            }
          }
        });
      },
      { threshold: options.threshold || 0.15 }
    );

    observer.observe(el);

    return () => {
      observer.disconnect();
    };
  }, [options.selector, options.distance, options.duration, options.stagger, options.delay, options.ease, options.once, options.threshold]);

  return ref;
}

/**
 * Hook to animate a numeric counter from 0 to target value when in view.
 */
export function useCounterAnimation(targetNumber, options = {}) {
  const ref = useRef(null);
  const animatedRef = useRef(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting && !animatedRef.current) {
            animatedRef.current = true;
            const obj = { val: 0 };
            animate(obj, {
              val: targetNumber,
              duration: options.duration || 1400,
              ease: 'outExpo',
              onUpdate: () => {
                if (el) {
                  const formatted = options.format
                    ? options.format(Math.round(obj.val))
                    : Math.round(obj.val).toLocaleString();
                  el.textContent = `${options.prefix || ''}${formatted}${options.suffix || ''}`;
                }
              },
            });
            observer.unobserve(el);
          }
        });
      },
      { threshold: 0.2 }
    );

    observer.observe(el);
    return () => observer.disconnect();
  }, [targetNumber, options.duration, options.format, options.prefix, options.suffix]);

  return ref;
}

export { animate, createTimeline, stagger };
