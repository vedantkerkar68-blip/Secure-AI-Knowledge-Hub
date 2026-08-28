import { useEffect, useRef, useState } from 'react';

const prefersReducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

/**
 * Animates a number from 0 to `target` with an ease-out curve.
 * Animates only on the first non-zero render; later value changes jump
 * instantly so background refreshes stay silent.
 */
export default function useCountUp(target, { duration = 600, decimals = 0 } = {}) {
  const [value, setValue] = useState(0);
  const rafRef = useRef(null);
  const animatedRef = useRef(false);

  useEffect(() => {
    if (prefersReducedMotion() || animatedRef.current) {
      setValue(target);
      return undefined;
    }
    animatedRef.current = true;

    let start;
    const from = 0;
    const tick = (now) => {
      if (start === undefined) start = now;
      const progress = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      setValue(from + (target - from) * eased);
      if (progress < 1) {
        rafRef.current = requestAnimationFrame(tick);
      } else {
        setValue(target);
      }
    };
    rafRef.current = requestAnimationFrame(tick);

    return () => {
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
    };
  }, [target, duration]);

  return Number(value.toFixed(decimals));
}