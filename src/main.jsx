import React from 'react'
import ReactDOM from 'react-dom/client'
import App from '@/App.jsx'
import '@/index.css'

// Blackboard redirects to this web page after authorization. When the
// connection was started from the native iOS app (state prefixed "ios."),
// hand the code straight back to the app instead of finishing it here.
function forwardBlackboardToIosApp() {
  if (window.location.pathname !== '/blackboard/callback') return false;
  const params = new URLSearchParams(window.location.search);
  if (!(params.get('state') || '').startsWith('ios.')) return false;
  window.location.replace(`studyspot://blackboard/callback?${params.toString()}`);
  return true;
}

if (!forwardBlackboardToIosApp()) {
  ReactDOM.createRoot(document.getElementById('root')).render(
    <App />
  )
}
