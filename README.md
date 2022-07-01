# OSC Spirograph

A Clerk Notebook with a demo for OSC driven graphic interactions. A "static" version is available here https://zampino.github.io/osc-spirograph.

https://user-images.githubusercontent.com/1078464/176853884-d46926df-d994-4266-9877-71800d84879b.mp4

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
