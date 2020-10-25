.PHONY: samples
samples: src/test/resources/INSERT.bin src/test/resources/UPDATE.bin src/test/resources/DELETE.bin

src/test/resources/INSERT.bin: 
	mkdir -p "src/test/resources"
	make/bin/capture-change.py \
		--change-file="src/test/resources/INSERT.bin" \
		-v new_first_name="Albert" \
		-v new_last_name="Einstein" \
		-- \
			"INSERT INTO 
				persons(
					first_name, 
					last_name
				) 
			VALUES(
				:new_first_name, 
				:new_last_name
			)"
	
src/test/resources/UPDATE.bin: src/test/resources/INSERT.bin
	make/bin/capture-change.py \
		--change-file="src/test/resources/UPDATE.bin" \
		-v old_last_name="Einstein" \
		-v new_first_name="Niels" \
		-v new_last_name="Bohr" \
		-- \
			"UPDATE 
				persons
			SET 
				first_name=:new_first_name,
				last_name=:new_last_name
			WHERE
				last_name=:old_last_name"

src/test/resources/DELETE.bin: src/test/resources/UPDATE.bin
	make/bin/capture-change.py \
		--change-file="src/test/resources/DELETE.bin" \
		-v old_last_name="Einstein" \
		-- \
			"DELETE FROM 
				persons 
			WHERE 
				last_name=:old_last_name"



