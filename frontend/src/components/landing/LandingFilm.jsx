import { useEffect, useRef, useState } from 'react';
import { Pause, Play } from 'lucide-react';

const POSTER = '/videos/SAED_Poster.jpg';

function usePrefersReducedMotion() {
  const [reduced, setReduced] = useState(
    () => typeof window !== 'undefined' && !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  );
  useEffect(() => {
    const mq = window.matchMedia?.('(prefers-reduced-motion: reduce)');
    if (!mq) return undefined;
    const onChange = (e) => setReduced(e.matches);
    mq.addEventListener?.('change', onChange);
    return () => mq.removeEventListener?.('change', onChange);
  }, []);
  return reduced;
}

export default function LandingFilm() {
  const reduced = usePrefersReducedMotion();
  const videoRef = useRef(null);
  const userPaused = useRef(false);
  const [playing, setPlaying] = useState(false);

  useEffect(() => {
    const video = videoRef.current;
    if (reduced || !video || typeof IntersectionObserver === 'undefined') return undefined;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (!entry.isIntersecting) {
          video.pause();
        } else if (!userPaused.current) {
          video.play().catch(() => {});
        }
      },
      { threshold: 0.25 }
    );
    observer.observe(video);
    return () => observer.disconnect();
  }, [reduced]);

  const togglePlay = () => {
    const video = videoRef.current;
    if (!video) return;
    if (video.paused) {
      userPaused.current = false;
      video.play().catch(() => {});
    } else {
      userPaused.current = true;
      video.pause();
    }
  };

  return (
    <section id="conjunto" className="m-section" aria-labelledby="m-film-title">
      <div className="m-container">
        <div className="m-section-head">
          <h2 id="m-film-title" className="m-h2">
            El conjunto armado
          </h2>
          <p className="m-body">
            Con todas las piezas en su lugar, así se ven administración, portería y residente
            trabajando sobre el mismo registro.
          </p>
        </div>

        <figure className="m-screen">
          <div className="m-screen-frame">
            <video
              ref={videoRef}
              className="m-screen-media"
              autoPlay={!reduced}
              muted
              loop
              playsInline
              controls={reduced}
              preload="metadata"
              poster={POSTER}
              onPlay={() => setPlaying(true)}
              onPause={() => setPlaying(false)}
              aria-label="Video de 15 segundos con pantallas ilustrativas de SAED"
            >
              <source src="/videos/SAED_Landing_15s_Web.webm" type="video/webm" />
              <source src="/videos/SAED_Landing_15s_FullHD.mp4" type="video/mp4" />
            </video>
          </div>
          <figcaption className="m-screen-bar">
            {!reduced && (
              <button
                type="button"
                className="m-icon-btn"
                onClick={togglePlay}
                aria-label={playing ? 'Pausar video' : 'Reproducir video'}
              >
                {playing ? <Pause aria-hidden="true" /> : <Play aria-hidden="true" />}
              </button>
            )}
            <span>Pantallas ilustrativas</span>
          </figcaption>
        </figure>
      </div>
    </section>
  );
}
