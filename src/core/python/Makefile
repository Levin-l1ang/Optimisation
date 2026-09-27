.PHONY: docs

docs:
	@rm -rf docs
	@mkdir docs
	cd docs && sphinx-quickstart -q -p "LinTim python core" -a "Department of Mathematics and Systems Analysis, Aalto University, Espoo and Optimization Research Group, RPTU Kaiserslautern-Landau and Fraunhofer ITWM, Kaiserslautern" --ext-autodoc --ext-todo --ext-viewcode
	cd docs && sphinx-apidoc -q --separate -o . ..
	@cat docs/index.rst > temp_index.rst
	@echo "   modules" >> temp_index.rst
	@mv temp_index.rst docs/index.rst
	@echo "import sys" > temp_conf.py
	@echo "import os" >> temp_conf.py
	@echo "sys.path.insert(0, os.path.abspath(\"..\"))" >> temp_conf.py
	@cat docs/conf.py >> temp_conf.py
	@echo "html_theme = 'sphinx_rtd_theme'" >> temp_conf.py
	@echo "autodoc_default_options = {'special-members': '__init__'}" >> temp_conf.py
	@mv temp_conf.py docs/conf.py
	cd docs && make html
	@echo "\n\nHTML documentation successfully created! Open src/core/python/docs/_build/html/index.html with your browser to access it."