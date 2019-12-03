# Admin UI

## Installation

      1. cp env.sample .env
      2. *EDIT* .env
      3. *CHANGE* bigdawg-postgres-catalog to localhost 
      4. *INSTALL* flask (see below)
      5. export FLASK_APP=app.py
      6. flask run --host=0.0.0.0
      
### Installation of flask

This application should work on both python 2.7 and 3

You can check which version of python you have installed by doing:

   * python --version

If there's no python you may need to install it

   * Ubuntu / Debian-based
      - sudo apt-get install -y python python-pip

You may want to install virtualenv to manage environments first:

   * sudo pip install virtualenv
      - If there's no pip, see above about installing python-pip

   * virtualenv -p /usr/bin/python venv

Then each time you want to use the program, do this first:

   * source venv/bin/activate

Now install the requirements:

   * pip install -r requirements.txt

Now test that flask is installed:

   * flask --version


 
