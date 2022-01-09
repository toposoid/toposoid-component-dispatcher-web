# toposoid-component-dispatcher-web
This is a WEB API that works as a microservice within the Toposoid project.
Toposoid is a knowledge base construction platform.(see [Toposoid　Root Project](https://github.com/toposoid/toposoid.git))
This microservice integrates two major microservices. 
One is a microservice that analyzes the predicate argument structure of japanese sentences, 
and the other is a microservice that makes logical inferences.

[![Unit Test And Build Image Action](https://github.com/toposoid/toposoid-component-dispatcher-web/actions/workflows/action.yml/badge.svg?branch=main)](https://github.com/toposoid/toposoid-component-dispatcher-web/actions/workflows/action.yml)

<img width="1090" src="https://user-images.githubusercontent.com/82787843/148678962-ac4842eb-2da2-45e2-953d-39dd392d9b30.png">

## Requirements
* Docker version 20.10.x, or later
* docker-compose version 1.22.x

## Memory requirements
* Required: at least 8GB of RAM (The maximum heap memory size of the JVM is set to 6G (Application: 4G, Neo4J: 2G))
* Required: 60G or higher　of HDD

## Setup 
```bssh
docker-compose up -d
```
It takes more than 20 minutes to pull the Docker image for the first time.
## Usage
```bash
#Japanese
curl -X POST -H "Content-Type: application/json" -d '{
    "premise":[],
    "claim":[{"sentence":"案ずるより産むが易し。","lang": "ja_JP", "extentInfoJson":"{}"}]
}' http://localhost:9004/analyze
#English
curl -X POST -H "Content-Type: application/json" -d '{
    "premise":[],
    "claim":[{"sentence":"Our life is our art.", "lang": "en_US", "extentInfoJson":"{}"}]
}' http://localhost:9004/analyze
```

## Note
* This microservice uses 9004 as the default port.
* If you want to run in a remote environment or a virtual environment, change PRIVATE_IP_ADDRESS in docker-compose.yml according to your environment.

## License
toposoid/toposoid-component-dispatcher-web is Open Source software released under the [Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0.html).

## Author
* Makoto Kubodera([Linked Ideal LLC.](https://linked-ideal.com/))

Thank you!
