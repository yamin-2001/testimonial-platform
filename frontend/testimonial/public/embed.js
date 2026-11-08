/*
 * The testimonial embed.
 *
 * A self-contained script: it has no dependency on the React bundle and no
 * build step, so it can be pasted into any page on any site (PRD 4.8).
 *
 * Two rules shape everything here:
 *
 *   1. Nothing a visitor wrote is ever put into the page as markup. The
 *      testimonials are built with `document.createElement` and
 *      `textContent`, so a testimonial containing `<script>` shows the
 *      script rather than running it. `innerHTML` appears nowhere.
 *
 *   2. Nothing a visitor wrote can reach the styles either. The background
 *      colour is checked against a hex-colour pattern before it is used, and
 *      every class name the script adds is prefixed, so the host site's CSS
 *      cannot reach in and the embed's CSS cannot leak out.
 *
 * The script finds its placeholders by `data-testimonial-space`, fetches the
 * content from the public endpoint and renders it. A Space that has been
 * deleted answers 404, and the script then renders nothing at all -- it never
 * throws, so the host page keeps working (PRD 4.8).
 */
(function () {
  "use strict";

  var ROOT_CLASS = "tw-embed";
  var CARD_CLASS = "tw-embed-card";
  var STYLE_ID = "tw-embed-styles";

  /**
   * The origin the content endpoint is served from.
   *
   * Taken from the script's own URL while the script is running, because
   * `document.currentScript` is null by the time an async script's callback
   * runs. Deriving it this way means the widget works wherever the script is
   * hosted from: the script and the data always come from the same place, and
   * the host page does not have to know about the API at all.
   */
  var API_BASE = (function () {
    var script = document.currentScript;
    if (script && script.src) {
      return new URL("/", script.src).href.replace(/\/$/, "");
    }
    return "";
  })();

  /**
   * The styles the embed uses.
   *
   * Every rule is scoped under `.tw-embed`, so the host site's CSS cannot
   * break the widget and the widget's CSS cannot break the host site
   * (PRD 8). The rules use only properties that are hard to override by
   * accident, and they set the properties they care about explicitly.
   */
  var CSS = [
    ".tw-embed{",
    "font-family:ui-sans-serif,system-ui,-apple-system,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif;",
    "max-width:100%;",
    "}",
    ".tw-embed *{box-sizing:border-box;}",
    ".tw-embed-heading{margin:0 0 4px;font-size:16px;font-weight:700;line-height:1.3;}",
    ".tw-embed-subtitle{margin:0 0 16px;font-size:13px;opacity:0.7;line-height:1.4;}",
    ".tw-embed-grid{display:grid;gap:16px;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));}",
    ".tw-embed-carousel{display:flex;gap:16px;overflow-x:auto;scroll-snap-type:x mandatory;padding-bottom:8px;}",
    ".tw-embed-carousel .tw-embed-card{flex:0 0 280px;scroll-snap-align:start;}",
    ".tw-embed-card{border:1px solid rgba(127,127,127,0.25);border-radius:12px;padding:16px;}",
    ".tw-embed-card-name{margin:0;font-size:14px;font-weight:600;line-height:1.3;}",
    ".tw-embed-card-meta{margin:2px 0 0;font-size:12px;opacity:0.65;line-height:1.4;}",
    ".tw-embed-card-message{margin:10px 0 0;font-size:14px;line-height:1.5;white-space:pre-wrap;overflow-wrap:anywhere;}",
    ".tw-embed-stars{margin:0 0 6px;font-size:14px;letter-spacing:1px;line-height:1;}",
    ".tw-embed-link{color:inherit;}",
    ".tw-embed-empty{margin:0;font-size:13px;opacity:0.7;}",
    ".tw-embed-light{color:#18181b;background:#ffffff;}",
    ".tw-embed-dark{color:#fafafa;background:#18181b;}",
    ".tw-embed-light .tw-embed-card{background:#fafafa;}",
    ".tw-embed-dark .tw-embed-card{background:#27272a;}",
    ".tw-embed-animate .tw-embed-card{animation:tw-embed-in 400ms ease-out both;}",
    "@keyframes tw-embed-in{from{opacity:0;transform:translateY(8px);}to{opacity:1;transform:none;}}",
    "@media (prefers-reduced-motion:reduce){",
    ".tw-embed-animate .tw-embed-card{animation:none;}",
    "}",
  ].join("");

  /**
   * Adds the styles once, however many embeds the page has.
   */
  function ensureStyles() {
    if (document.getElementById(STYLE_ID)) {
      return;
    }

    var style = document.createElement("style");
    style.id = STYLE_ID;
    style.textContent = CSS;
    document.head.appendChild(style);
  }

  /**
   * Whether a value is a plain hex colour.
   *
   * The background colour ends up in an inline style on somebody else's page,
   * so anything that is not plainly a colour is refused rather than passed
   * through.
   */
  function isHexColour(value) {
    if (typeof value !== "string") {
      return false;
    }
    return /^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/.test(value.trim());
  }

  /**
   * Builds an element and fills it.
   *
   * The text is set with `textContent`, never with `innerHTML`, so a
   * testimonial that contains markup is shown as text rather than being
   * parsed as markup (PRD 4.8).
   */
  function element(tag, className, text) {
    var node = document.createElement(tag);
    if (className) {
      node.className = className;
    }
    if (text !== undefined && text !== null) {
      node.textContent = String(text);
    }
    return node;
  }

  /**
   * Renders one testimonial.
   */
  function renderTestimonial(testimonial) {
    var card = element("article", CARD_CLASS);

    if (typeof testimonial.rating === "number" && testimonial.rating >= 1) {
      var stars = element("div", "tw-embed-stars", "★".repeat(Math.min(5, testimonial.rating)));
      stars.setAttribute("role", "img");
      stars.setAttribute("aria-label", testimonial.rating + " stars");
      card.appendChild(stars);
    }

    card.appendChild(element("p", "tw-embed-card-name", testimonial.name));

    // The company name and the job title sit on one line, and only when the
    // Space publishes them.
    var details = [];
    if (testimonial.companyName) {
      details.push(testimonial.companyName);
    }
    if (testimonial.jobTitle) {
      details.push(testimonial.jobTitle);
    }
    if (details.length > 0) {
      card.appendChild(element("p", "tw-embed-card-meta", details.join(" · ")));
    }

    card.appendChild(element("p", "tw-embed-card-message", testimonial.message));

    // The website and the social links are links, but only http and https
    // ones. The link text is the URL, and the URL is set as an attribute
    // rather than interpolated into markup.
    var links = [];
    if (testimonial.websiteUrl) {
      links.push(testimonial.websiteUrl);
    }
    if (Array.isArray(testimonial.socialLinks)) {
      for (var i = 0; i < testimonial.socialLinks.length; i++) {
        links.push(testimonial.socialLinks[i]);
      }
    }

    for (var j = 0; j < links.length; j++) {
      var url = String(links[j]);
      if (!/^https?:\/\//i.test(url)) {
        continue;
      }

      var link = element("a", "tw-embed-link", url);
      link.setAttribute("href", url);
      link.setAttribute("rel", "noopener noreferrer nofollow");
      link.setAttribute("target", "_blank");
      card.appendChild(element("p", "tw-embed-card-meta")).appendChild(link);
    }

    return card;
  }

  /**
   * Renders the whole widget into a placeholder.
   */
  function render(placeholder, content) {
    placeholder.textContent = "";

    var settings = content.settings || {};
    var mode = settings.mode === "dark" ? "dark" : "light";
    var layout = settings.layout === "carousel" ? "carousel" : "masonry";

    var root = element("div", ROOT_CLASS + " tw-embed-" + mode);
    if (settings.animation) {
      root.className += " tw-embed-animate";
    }
    if (isHexColour(settings.backgroundColor)) {
      root.style.backgroundColor = settings.backgroundColor.trim();
    }

    if (content.title) {
      root.appendChild(element("h2", "tw-embed-heading", content.title));
    }
    if (content.subtitle) {
      root.appendChild(element("p", "tw-embed-subtitle", content.subtitle));
    }

    var testimonials = Array.isArray(content.testimonials) ? content.testimonials : [];

    if (testimonials.length === 0) {
      root.appendChild(element("p", "tw-embed-empty", "No testimonials yet."));
    } else {
      var list = element("div", layout === "carousel" ? "tw-embed-carousel" : "tw-embed-grid");
      for (var i = 0; i < testimonials.length; i++) {
        list.appendChild(renderTestimonial(testimonials[i]));
      }
      root.appendChild(list);
    }

    placeholder.appendChild(root);
  }

  /**
   * Loads one placeholder's content and renders it.
   *
   * Any failure -- a deleted Space, a network error, a malformed response --
   * leaves the placeholder empty. The host page never sees an error from the
   * embed (PRD 4.8).
   */
  function load(placeholder) {
    var slug = placeholder.getAttribute("data-testimonial-space");
    if (!slug) {
      return;
    }

    var url = API_BASE + "/api/public/embed/" + encodeURIComponent(slug);

    fetch(url, { credentials: "omit" })
      .then(function (response) {
        if (!response.ok) {
          throw new Error("The embed could not be loaded.");
        }
        return response.json();
      })
      .then(function (content) {
        render(placeholder, content);
      })
      .catch(function () {
        // Nothing is rendered. A deleted Space must not error the host page.
        placeholder.textContent = "";
      });
  }

  /**
   * Finds the placeholders and loads them.
   */
  function start() {
    var placeholders = document.querySelectorAll("[data-testimonial-space]");
    if (placeholders.length === 0) {
      return;
    }

    ensureStyles();

    for (var i = 0; i < placeholders.length; i++) {
      load(placeholders[i]);
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", start);
  } else {
    start();
  }
})();
