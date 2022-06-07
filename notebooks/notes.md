# Notes
## Setup

- tether
- setup OSC conn (in / out)
- open TouchOSC app / show examples
- open static page in tab
- quicktime iphone feedback
- uncollapse model viewer 

## Mention
- Hello I am X, I share the fun in bulding Clerk at Nextjournal
- toolset for turning a clojure namespace into a live web application
- present somewhat exotic example of a Clerk notebook in which 
- a connected OSC driven touch application interacts with the JVM
- which in turn synchronizes its state with the reagent state in the browser
- and allows to explore a simple model with 3 independent frequencies and amplitudes
- which gives rise a "closed" curve function of time, a spirograph
- you should be seeing the feedback of my phone screen here, changing parameters
- for the harmonic analysis freaks among you there's a wave mode of course 
- an explanation of the moving parts and the OSC protocol wiring
- start and connect a Java OSC server
- it's not disruptive of your repl flow / save models you produce with fingers
- local first / but also static publishing

## Old
- OSC binary protocol over UDP to communicate with a Clerk notebook
- Touch OSC for composing OSC based UI and mapping faders and sliders to messages and addresses
- as I interact you see values of the model changing here
- this is Two.js not Three.js
- OSC server listening - Java interop niceness
- update the model value and recompute
- prefer to work programmatically

- future plans
