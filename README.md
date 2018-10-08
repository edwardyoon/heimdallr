# Heimdallr

Heimdallr is a large-scale chat application server inspired LINE+ chat service architecture, written in Scala language based on Akka's actor model. It provides fault-tolerance and reliable scale-out options based on Redis Pubsub to support expansion from proof-of-concept to enterprise-ready solutions. This project is licensed Apache License v2.0. https://www.apache.org/licenses/LICENSE-2.0.txt

## Architecture

It consists of HTTP Server, ChatRoomActor, and UserActor. Each ChatRoom can be distributed across multiple servers. To synchronize the message among servers, we uses Redis PubSub. UserActor is created per every websocket client.

<p align="center">
  <img width="80%" src="https://github.com/edwardyoon/Heimdallr/blob/master/project/architecture.png?raw=true">
</p>


## Comparison w/ Node.js and Socket.io 

The below HTTP server test was done using wrk benchmarking tool with 6 threads and 10000 connections on m4.xlarge single instance.

|  | Node.js | Heimdallr | 
| :--- | :--- | :--- |
| Requests per sec | 14533.90 | 20675.89 |
| Avg. Latency | 68.94 ms | 13.35 ms |
| Summary | 873389 requests in 1.00m, 108.36MB read<br>Socket errors: connect 8981, read 0, write 0, timeout 0<br>Requests/sec:  14533.90<br>Transfer/sec:      1.80MB | 1242244 requests in 1.00m, 181.26MB read<br>Socket errors: connect 8981, read 0, write 0, timeout 0<br>Requests/sec:  20675.89<br>Transfer/sec:      3.02MB |

This table shows the performance of event broadcasting, the average latency time until message arrives at websocket client. The test was done on same m4.xlarge single instance.

|  | Socket.io | Heimdallr | 
| :---: | :---: | :---: |
| 10 sub, 1 pub | 43 ms | 43 ms |
| 100 sub, 5 pub | 62 ms | 61 ms |
| 1000 sub, 10 pub | 496 ms | 390 ms |
| 1000 sub, 50 pub | 1304 ms | 554 ms |
| 1000 sub, 100 pub | 2242 ms | 605 ms |

## Getting Started

Clone the repository and try to build with sbt:

`% sbt run`

