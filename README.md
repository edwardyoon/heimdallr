# Heimdallr

Heimdallr is a large-scale chat application server written in Scala language based on Akka's actor model. It provides fault-tolerance and reliable scale-out options based on Redis Pubsub to support expansion from proof-of-concept to enterprise-ready solutions. This project is licensed Apache License v2.0. https://www.apache.org/licenses/LICENSE-2.0.txt

## Comparison w/ Node.js and Socket.io 

|  | Node.js | Akka HTTP | 
| :---: | :---: | :---: |
| Requests per sec | 14533.90 | 20675.89 |
| Avg. Latency | 68.94 ms | 13.35 ms |

|  | Socket.io | Akka | 
| :---: | :---: | :---: |
| 10 sub, 1 pub | 43 ms | 43 ms |
| 100 sub, 5 pub | 62 ms | 61 ms |
| 1000 sub, 10 pub | 496 ms | 390 ms |
| 1000 sub, 50 pub | 1304 ms | 554 ms |
| 1000 sub, 100 pub | 2242 ms | 605 ms |

## Getting Started

Clone the repository and try to build with sbt:

`% sbt run`

