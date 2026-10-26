import { useEffect } from 'react';

// Elements that assemble as they scroll into view. Content stays visible unless JS
// runs, motion is allowed, and the element starts below the fold.
const SELECTOR = '.m-section-head, [data-reveal-group], .m-spec-table, .m-unbuilt-list, .m-faq';

export default function useManualReveal(rootRef) {
  useEffect(() => {
    const root = rootRef.current;
    const reduced = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    if (!root || reduced || typeof IntersectionObserver === 'undefined') return undefined;

    const targets = Array.from(root.querySelectorAll(SELECTOR)).filter(
      (el) => el.getBoundingClientRect().top > window.innerHeight * 0.9
    );
    targets.forEach((el) => {
      el.dataset.reveal = 'pending';
    });

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) return;
          entry.target.dataset.reveal = 'in';
          observer.unobserve(entry.target);
        });
      },
      { rootMargin: '0px 0px -12% 0px', threshold: 0.05 }
    );
    targets.forEach((el) => observer.observe(el));
    return () => observer.disconnect();
  }, [rootRef]);
}
