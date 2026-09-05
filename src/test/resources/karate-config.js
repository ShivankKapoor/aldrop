function fn() {
  // reads admin credentials from the OS environment (not .env directly) so
  // nothing from a real .env ever ends up hardcoded/committed here. Export
  // PLATFORM_ADMIN_USERNAME/PLATFORM_ADMIN_PASSWORD in your shell (or set
  // them on the run config) to match whatever the live app was started with.
  var System = Java.type('java.lang.System');

  var config = {
    baseUrl: 'http://localhost:4000',
    adminUsername: System.getenv('PLATFORM_ADMIN_USERNAME') || 'admin',
    adminPassword: System.getenv('PLATFORM_ADMIN_PASSWORD') || 'changeme'
  };

  karate.configure('connectTimeout', 5000);
  karate.configure('readTimeout', 10000);

  return config;
}
