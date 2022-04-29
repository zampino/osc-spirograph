# OSC Spirograph

A Clerk Notebook with a demo for OSC driven graphic interactions. A "static" version is available here https://zampino.github.io/osc-spirograph.

## Usage

Checkout, start your REPL, boot Clerk

```clojure
(nextjournal.clerk/serve! {})
```

show the notebook

```clojure
(nextjournal.clerk/show! "notebooks/osc_spirograph.clj")
```

and visit localhost:7777 in your browser. To interact with the spirograph load [the UI configuration](spirograph.tosc) into your [touch OSC app](https://hexler.net/touchosc) and point its OSC connection to port `6669`.
