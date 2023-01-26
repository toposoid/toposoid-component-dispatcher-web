# toposoid-component-dispatcher-web
This is a WEB API that works as a microservice within the Toposoid project.
Toposoid is a knowledge base construction platform.(see [Toposoid　Root Project](https://github.com/toposoid/toposoid.git))
This microservice integrates two major microservices. 
One is a microservice that analyzes the predicate argument structure of japanese sentences, 
and the other is a microservice that makes logical inferences.

[![Unit Test And Build Image Action](https://github.com/toposoid/toposoid-component-dispatcher-web/actions/workflows/action.yml/badge.svg?branch=main)](https://github.com/toposoid/toposoid-component-dispatcher-web/actions/workflows/action.yml)

- API Image
<img width="1127" alt="" src="https://user-images.githubusercontent.com/82787843/169644935-1386320e-e436-4dca-922f-4f2701fcd9bc.png">

<img width="1131" alt="" src="https://user-images.githubusercontent.com/82787843/169644939-05b7b625-4188-4002-a46b-e4d03c1ea669.png">

## Requirements
* Docker version 20.10.x, or later
* docker-compose version 1.22.x

## Memory requirements
* Required: at least 8GB of RAM (The maximum heap memory size of the JVM is set to 6G (Application: 4G, Neo4J: 2G))
* Required: 60G or higher　of HDD

## Setup 
```bssh
rm -f vald-config/backup/* && docker-compose up -d
```
* It takes more than 20 minutes to pull the Docker image for the first time.
* **The docker-compose.yml configuration in this repository does not take into account vald and neo4j persistence.**
* If vald does not start due to an error, commenting out the following part in docker-compose.yml may work.
```yml
  vald:
    image: vdaas/vald-agent-ngt:v1.6.3
    #user: 1000:1000
    volumes:
      - ./vald-config:/etc/server
      #- /etc/passwd:/etc/passwd:ro
      #- /etc/group:/etc/group:ro
    networks:
      app_net:
        ipv4_address: 172.30.0.10
    ports:
      - 8081:8081
```

## Usage
```bash
# This Json can also be expressed recursively as a binary tree.
curl -X POST -H "Content-Type: application/json" -d '{
    "regulation": {
        "knowledgeLeft": {
            "leaf": {
                "premiseList": [],
                "premiseLogicRelation": [],
                "claimList": [],
                "claimLogicRelation": []
            }
        },
        "operator": "",
        "knowledgeRight": {
            "leaf": {
                "premiseList": [],
                "premiseLogicRelation": [],
                "claimList": [
                    {
                        "sentence": "これは主張1です。",
                        "lang": "ja_JP",
                        "extentInfoJson": "{}",
                        "isNegativeSentence": false
                    }
                ],
                "claimLogicRelation": []
            }
        }
    },
    "hypothesis": {
        "knowledgeLeft": {
            "leaf": {
                "premiseList": [],
                "premiseLogicRelation": [],
                "claimList": [],
                "claimLogicRelation": []
            }
        },
        "operator": "",
        "knowledgeRight": {
            "leaf": {
                "premiseList": [],
                "premiseLogicRelation": [],
                "claimList": [
                    {
                        "sentence": "これは前提1です。",
                        "lang": "ja_JP",
                        "extentInfoJson": "{}",
                        "isNegativeSentence": false
                    }
                ],
                "claimLogicRelation": []
            }
        }
    }
}' http://localhost:9004/analyzeKnowledgeTree

#If you just want to search the knowledge base
curl -X POST -H "Content-Type: application/json" -d '{
    "premise": [],
    "claim": [
        {
            "sentence": "案ずるより産むが易し。",
            "lang": "ja_JP",
            "extentInfoJson": "{}",
            "isNegativeSentence": false
        }
    ]
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
