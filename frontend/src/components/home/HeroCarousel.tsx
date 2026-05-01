import { useEffect, useState } from 'react';

interface Slide {
  heading: string;
  bgGradient: string;
}

const SLIDES: Slide[] = [
  { heading: 'Elektronikte Fırsatlar', bgGradient: 'bg-gradient-to-r from-blue-700 to-purple-700' },
  { heading: 'Yeni Sezon Moda', bgGradient: 'bg-gradient-to-r from-pink-600 to-rose-700' },
  { heading: 'Ev & Yaşam Kampanyaları', bgGradient: 'bg-gradient-to-r from-emerald-700 to-teal-800' },
];

const ADVANCE_MS = 5000;

export function HeroCarousel() {
  const [index, setIndex] = useState(0);

  useEffect(() => {
    const id = setInterval(() => setIndex(i => (i + 1) % SLIDES.length), ADVANCE_MS);
    return () => clearInterval(id);
  }, []);

  const current = SLIDES[index]!;

  return (
    <section aria-label="Ana sayfa kampanya alanı" className="relative w-full">
      <div
        className={`${current.bgGradient} h-[280px] md:h-[360px] flex items-center justify-center text-white transition-colors duration-700`}
      >
        <h2 className="text-3xl md:text-5xl font-bold">{current.heading}</h2>
      </div>
      <div className="absolute bottom-4 left-0 right-0 flex justify-center gap-2">
        {SLIDES.map((_, i) => (
          <button
            key={i}
            type="button"
            onClick={() => setIndex(i)}
            aria-label={`Slayt ${i + 1}`}
            aria-current={i === index ? 'true' : undefined}
            className={`h-2 rounded-full transition-all ${
              i === index ? 'w-8 bg-white' : 'w-2 bg-white/60'
            }`}
          />
        ))}
      </div>
    </section>
  );
}
