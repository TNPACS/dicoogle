"use strict";

var browserify = require("browserify");
var buffer = require("vinyl-buffer");
var eslint = require("gulp-eslint");
var gulp = require("gulp");
var gutil = require("gulp-util");
var processhtml = require("gulp-processhtml");
var rename = require("gulp-rename");
var rm = require("gulp-rm");
var sass = require("gulp-sass");
var source = require("vinyl-source-stream");
var sourcemaps = require("gulp-sourcemaps");
var watchify = require("watchify");

require("core-js/fn/object/assign");

var EXTERNAL_REQUIRES = [
  "react",
  "react-router",
  "reflux",
  "dicoogle-webcore",
  "dicoogle-client",
  "react-bootstrap",
  "react-router-bootstrap",
  "react-bootstrap-table",
  "react-imageloader",
  "react-dom"
];

function createBrowserify(debug, watch) {
  // set up the browserify instance on a task basis
  var b = browserify("./js/app.js", {
    cache: {},
    packageCache: {},
    extensions: [".jsx"],
    debug: debug
  }).transform("envify", {
    _: "purge",
    global: true,
    NODE_ENV: debug ? "development" : "production"
  });

  if (debug) {
    b.transform("babelify", {
      presets: ["@babel/preset-env", "@babel/preset-react"]
    });
  } else {
    b.transform("babelify", {
      presets: ["@babel/preset-env", "@babel/preset-react", "minify"]
    });
  }
  if (watch) {
    b.plugin(watchify);
  }
  return b.require(EXTERNAL_REQUIRES);
}

function productionEnv(cb) {
  process.env.NODE_ENV = "production";
  cb();
}
exports["production-env"] = productionEnv;

function lint() {
  return gulp
    .src(["js/**/*.js", "js/**/*.jsx"])
    .pipe(
      eslint({
        configFile: ".eslintrc"
      })
    )
    .pipe(eslint.format())
    .pipe(eslint.failAfterError());
}
exports.lint = lint;

function handleBundlingError(e) {
  gutil.log("" + e);
}

function js() {
  return createBrowserify(false, false)
    .bundle()
    .on("error", handleBundlingError)
    .pipe(source("bundle.min.js"))
    .pipe(buffer())
    .pipe(gulp.dest("lib"));
}
exports.js = js;

function jsDebug() {
  return createBrowserify(true, false)
    .bundle()
    .on("error", handleBundlingError)
    .pipe(source("bundle.js"))
    .pipe(buffer())
    .pipe(sourcemaps.init({ loadMaps: true }))
    .pipe(sourcemaps.write("./"))
    .pipe(gulp.dest("lib"));
}
exports["js-debug"] = jsDebug;

function jsWatch() {
  var b = createBrowserify(true, true);
  b.on("update", bundle); // on any dep update, runs the bundler
  b.on("log", gutil.log); // output build logs to terminal

  function bundle() {
    return (
      b
        .bundle()
        .on("error", handleBundlingError)
        .pipe(source("bundle.js"))
        .pipe(buffer())
        .pipe(sourcemaps.init({ loadMaps: true })) // loads map from browserify file
        // Add transformation tasks to the pipeline here.
        .pipe(sourcemaps.write("./")) // writes .map file
        .pipe(gulp.dest("lib"))
    );
  }
  bundle();
}
exports["js:watch"] = jsWatch;

function html() {
  // use processhtml
  return gulp
    .src("index-template.html")
    .pipe(
      processhtml({
        environment: "dist",
        strip: true
      })
    )
    .pipe(rename("index.html"))
    .pipe(gulp.dest("."));
}
exports.html = html;

function htmlDebug() {
  // use processhtml
  return gulp
    .src("index-template.html")
    .pipe(
      processhtml({
        environment: "dev"
      })
    )
    .pipe(rename("index.html"))
    .pipe(gulp.dest("."));
}
exports["html-debug"] = htmlDebug;

function css() {
  // use sass
  return gulp
    .src("sass/dicoogle.scss")
    .pipe(sass({ outputStyle: "compressed" }).on("error", sass.logError))
    .pipe(gulp.dest("css"));
}
exports.css = css;

function cssDebug() {
  // use sass
  return gulp
    .src("sass/dicoogle.scss")
    .pipe(sourcemaps.init())
    .pipe(sass().on("error", sass.logError))
    .pipe(sourcemaps.write())
    .pipe(gulp.dest("css"));
}
exports["css-debug"] = cssDebug;

function cssWatch() {
  gulp.watch("sass/**/*.scss", ["css-debug"]);
}
exports["css:watch"] = cssWatch;

exports.production = gulp.series(
  productionEnv,
  gulp.parallel(gulp.series(lint, js), html, css)
);
exports.development = gulp.parallel(
  gulp.series(lint, jsDebug),
  htmlDebug,
  cssDebug
);

function clean() {
  return gulp
    .src(["lib/bundle.*", "css/dicoogle.css*", "index.html"], { read: false })
    .pipe(rm());
}
exports.clean = clean;

exports.default = gulp.task("production");
