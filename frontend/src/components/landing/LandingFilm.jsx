import { useEffect, useRef, useState } from 'react';
import { Pause, Play } from 'lucide-react';

const POSTER = '/videos/SAED_Poster.jpg';
const WEBM = '/videos/SAED_Landing_15s_Web.webm';
const MP4 = '/videos/SAED_Landing_15s_FullHD.mp4';

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

  // A decode error after media data has loaded does not fall through to the next <source>,
  // so switch to the MP4 explicitly.
  const handleError = () => {
    const video = videoRef.current;
    if (!video || video.currentSrc.endsWith('.mp4')) return;
    video.src = MP4;
    video.load();
    if (!reduced && !userPaused.current) video.play().catch(() => {});
  };

  // Fallback in case `loop` is ignored by the engine.
  const handleEnded = () => {
    const video = videoRef.current;
    if (!video || reduced || userPaused.current) return;
    video.currentTime = 0;
    video.play().catch(() => {});
  };

  return (
    <section id="inicio" className="m-section m-film" aria-labelledby="m-film-title">
      <div className="m-container">
        <div className="m-film-head">
          <h1 id="m-film-title" className="m-display m-film-title">
            SAED en 15 segundos
          </h1>
          <p className="m-lede">
            Administración, portería y residentes trabajando sobre el mismo registro de tu conjunto.
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
              onEnded={handleEnded}
              onError={handleError}
              aria-label="Video de 15 segundos con pantallas ilustrativas de SAED"
            >
              <source src={WEBM} type="video/webm" onError={handleError} />
              <source src={MP4} type="video/mp4" />
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
