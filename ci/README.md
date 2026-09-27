# How to create new CI tests

- Copy the content of the `template` folder into a new folder in `/ci/`
- Modify the file parameters.csv
    - Specify after `TARGET_DATASET` the name of an existing dataset in the datasets folder
    - Enter the commands to run after `COMMANDS_TO_RUN` seperated with `&&`
    - If additionial files should be compared, enter them after `ADDITIONAL_FILES_TO_COMPARE` seperated with `,`
- Enter the necessary config data into basis/Private-Config.cnf
- Enter the expected outcome into expected-statistic.sta