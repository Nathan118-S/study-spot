// Ambient "live" background: a few large, soft brand-colored glows drifting
// slowly behind the page. Pure CSS (radial gradients + transform
// animations, no blur filters), so it's cheap to render. It stops moving
// when animations are turned off in Settings or the system asks for reduced
// motion (see index.css).
export default function LiveBackground() {
  return (
    <div aria-hidden className="live-bg pointer-events-none fixed inset-0 -z-10 overflow-hidden">
      <div className="live-bg__blob live-bg__blob--1" />
      <div className="live-bg__blob live-bg__blob--2" />
      <div className="live-bg__blob live-bg__blob--3" />
      <div className="live-bg__blob live-bg__blob--4" />
    </div>
  );
}
