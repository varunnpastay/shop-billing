/** Tailwind setup for the shop app. Run "npm run build" in this folder after changing templates or app.css. */
module.exports = {
  content: [
    "../src/main/resources/templates/**/*.html",
    "../src/main/resources/static/js/**/*.js",
  ],
  theme: {
    extend: {
      colors: {
        navy: {
          50: "#f1f5fb", 100: "#e3eaf6", 200: "#c5d1e3", 300: "#a9b9d2", 400: "#7d8fae", 500: "#5b6b85",
          600: "#1f4682", 700: "#183764", 800: "#112a4d", 900: "#0b1f3a", 950: "#071428",
        },
        gold: { 300: "#ffe7a3", 400: "#ffc85c", 500: "#f2b33d", 600: "#d99a1e" },
        ink: "#13213a",
        page: "#eef2f8",
        line: "#dbe3ef",
      },
      fontFamily: {
        sans: ['"Segoe UI"', "system-ui", "-apple-system", "Roboto", "Arial", "sans-serif"],
        brand: ['"Cinzel"', "Georgia", '"Times New Roman"', "serif"],
      },
      boxShadow: {
        card: "0 1px 2px rgba(11,31,58,.06)",
        lift: "0 4px 16px rgba(11,31,58,.08)",
      },
    },
  },
  plugins: [],
};
