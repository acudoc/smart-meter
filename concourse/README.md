fly -t cicd login --team-name deetazilla-team -c http://ci.logimethodslabs.com:8080

fly -t cicd set-pipeline -p smart_meter-pipeline-DEV -c smart_meter-pipeline.yml --load-vars-from properties.yml --load-vars-from properties_branch.yml --load-vars-from credentials.yml