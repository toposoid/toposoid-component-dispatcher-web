# toposoid-component-dispatcher-web
This is a WEB API that works as a microservice within the Toposoid project.
Toposoid is a knowledge base construction platform.(see [Toposoid　Root Project](https://github.com/toposoid/toposoid.git))
This microservice integrates two major microservices. 
One is a microservice that analyzes the predicate argument structure of japanese sentences, 
and the other is a microservice that makes logical inferences.

[![Unit Test And Build Image Action](https://github.com/toposoid/toposoid-component-dispatcher-web/actions/workflows/action.yml/badge.svg?branch=main)](https://github.com/toposoid/toposoid-component-dispatcher-web/actions/workflows/action.yml)

* API Image
    * Input
    * <img width="1026" src="https://github.com/toposoid/toposoid-component-dispatcher-web/assets/82787843/f0e6d47a-81ab-4f17-b305-18f61f1efe6e">
    * Output
    * <img width="1022" src="https://github.com/toposoid/toposoid-component-dispatcher-web/assets/82787843/9fab744a-31f1-41b8-be80-68bd0c5484b5">
    
## Requirements
* Docker version 20.10.x, or later
* docker-compose version 1.22.x

### Recommended Environment For Standalone
* Required: at least 16GB of RAM
* Required: 60G or higher　of HDD
* Please understand that since we are dealing with large models such as LLM, the Dockerfile size is large and the required machine SPEC is high.

## Setup For Standalone 
```bssh
docker-compose up
```
* It takes more than 20 minutes to pull the Docker image for the first time.

## Usage
```bash
# Please refer to the following for information on registering data to try deduction.
# ref. https://github.com/toposoid/toposoid-knowledge-register-web
#for example
curl -X POST -H "Content-Type: application/json" -H 'X_TOPOSOID_TRANSVERSAL_STATE: {"userId":"test-user", "username":"guest", "roleId":0, "csrfToken":""}' -d '{
    "premiseList": [],
    "premiseLogicRelation": [],
    "claimList": [
        {
            "sentence": "これは主張1です。",
            "lang": "ja_JP",
            "extentInfoJson": "{}",
            "isNegativeSentence": false,
            "knowledgeForImages":[]
        }
    ],
    "claimLogicRelation": [
    ]
}
' http://localhost:9002/regist


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
                        "isNegativeSentence": false,
                        "knowledgeForImages":[]
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
                        "isNegativeSentence": false,
                        "knowledgeForImages":[]
                    }
                ],
                "claimLogicRelation": []
            }
        }
    }
}' http://localhost:9004/analyzeKnowledgeTree

```

## Note
* This microservice uses 9004 as the default port.
* If you want to run in a remote environment or a virtual environment, change PRIVATE_IP_ADDRESS in docker-compose.yml according to your environment.

## License
This program is offered under a commercial and under the AGPL license.
For commercial licensing, contact us at https://toposoid.com/contact.  For AGPL licensing, see below.

AGPL licensing:
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.


## Author
* Makoto Kubodera([Linked Ideal LLC.](https://linked-ideal.com/))

Thank you!
