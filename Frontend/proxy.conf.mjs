// Dev-server proxy: the browser only ever talks to the Angular origin, so the auth cookie stays
// SameSite=Strict and Angular's XSRF header is sent. Override the backend with API_TARGET if needed.
export default {
  '/api': {
    target: process.env.API_TARGET ?? 'http://localhost:8080',
    secure: false,
    changeOrigin: false,
  },
};
