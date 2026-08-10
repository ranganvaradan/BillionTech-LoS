// Local Docker (no host nginx /plp-api). Copy or symlink to env-config.js:
//   copy env-config.local.js env-config.js
window.__ENV__ = {
  VITE_API_BASE_URL: 'http://localhost:8180',
};
