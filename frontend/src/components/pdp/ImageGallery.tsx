import { useState } from 'react';

interface Props {
  primaryUrl: string;
  altText: string;
  additionalUrls?: string[];  // forward-compat; v1 always undefined
}

export function ImageGallery({ primaryUrl, altText, additionalUrls = [] }: Props) {
  const allImages = [primaryUrl, ...additionalUrls].slice(0, 5);
  const [activeIdx, setActiveIdx] = useState(0);
  const active = allImages[activeIdx] ?? primaryUrl;

  return (
    <div>
      <div className="aspect-square bg-white border border-[var(--color-border)] rounded overflow-hidden">
        <img src={active} alt={altText} className="w-full h-full object-contain" />
      </div>
      {allImages.length > 1 && (
        <div className="flex gap-2 mt-3">
          {allImages.map((url, i) => (
            <button
              key={i}
              type="button"
              onClick={() => setActiveIdx(i)}
              aria-label={`Görsel ${i + 1}`}
              aria-current={i === activeIdx ? 'true' : undefined}
              className={`w-16 h-16 border-2 rounded overflow-hidden ${
                i === activeIdx ? 'border-[#1C1C1E]' : 'border-transparent'
              }`}
            >
              <img src={url} alt="" className="w-full h-full object-cover" />
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
