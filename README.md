# OSC Spirograph

A [Clerk Notebook](https://github.com/nextjournal/clerk) showing how to steer a vector graphic animation by an [Open Sound Control](https://en.wikipedia.org/wiki/Open_Sound_Control) touch device.

https://user-images.githubusercontent.com/1078464/176853884-d46926df-d994-4266-9877-71800d84879b.mp4

## Usage

A static build of the notebook is running at https://zampino.github.io/osc-spirograph but for all the interactive joy I recommend trying the notebook locally in interactive mode. In order to do that, start your REPL of choice and boot Clerk

```clojure
(nextjournal.clerk/serve! {})
```

show the notebook

```clojure
(nextjournal.clerk/show! "notebooks/osc_spirograph.clj")
```

and visit `localhost:7777` in the browser. To interact with the spirograph load [the UI configuration](spirograph.tosc) into your [touch OSC app](https://hexler.net/touchosc) and point its OSC connection to port `6669`.
