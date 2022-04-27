# OSC Spirograph

A Clerk Notebook with a demo for OSC driven graphic interactions. A "static" version is available here https://zampino.github.io/osc-spirograph.

## Usage

Checkout, launch REPL and eval 

```clojure
(nextjournal.clerk/show! "notebooks/osc_spirograph.clj")
```

To interact with the spirograph load [the UI configuration](spirograph.tosc) into your [touch OSC app](https://hexler.net/touchosc) and point its OSC connection to port `6669`.
