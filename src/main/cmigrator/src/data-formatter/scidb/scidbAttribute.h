#ifndef SCIDB_ATTRIBUTE_H
#define SCIDB_ATTRIBUTE_H

#include <endian.h>
#include <stdint.h>
#include <cstdio>
#include <iostream>
#include <assert.h>

#define __STDC_FORMAT_MACROS
#include <inttypes.h>

#include "../common/endianness.h"
#include "../common/formatterExceptions.h"
#include "../attribute/attribute.h"

template<class T>
class SciDBAttribute: public GenericAttribute<T> {

public:
	SciDBAttribute(const SciDBAttribute & obj);
	virtual ~SciDBAttribute();
	SciDBAttribute(FILE * fp, bool isNullable = false);
	virtual SciDBAttribute * clone() const;

	/** read the attribute value from the source file */
	virtual Attribute * read();

	/** write the attribute value to a source file */
	virtual void write(Attribute * attribute);
};

template<class T>
SciDBAttribute<T>::SciDBAttribute(const SciDBAttribute &obj) :
		GenericAttribute<T>(obj) {
	printf("Copy constructor scidb attribute.\n");
}

template<class T>
SciDBAttribute<T>::~SciDBAttribute() {
	printf("Freeing memory scidb attribute!\n");
}

template<class T>
SciDBAttribute<T> * SciDBAttribute<T>::clone() const {
	printf("%s", "clone scidb\n");
	return new SciDBAttribute(*this);
}

template<class T>
SciDBAttribute<T>::SciDBAttribute(FILE * fp, bool isNullable) :
		GenericAttribute<T>(fp, isNullable) {
	printf("Create a brand new scidb attribute!\n");
}

template<class T>
Attribute * SciDBAttribute<T>::read() {
	//std::cout << "this is type: " << boost::typeindex::type_id().pretty_name() << std::endl;
	if (this->isNullable) {
		/* read 1 byte that tells us if the attribute is null or not:
		 - If a nullable attribute contains a non-null value,
		 the preceding null byte is -1.
		 - If a nullable attribute contains a null value,
		 the preceding null byte will contain the missing reason code,
		 which must be between 0 and 127
		 */
		int8_t nullValue;
		size_t bytes_read;
		bytes_read = fread(&nullValue, 1, 1, this->fp);
		if (bytes_read != 1) {
			std::string message(
					"No more data in the input file while reading data from "
							"the binary scidb file.");
			throw DataMigratorException(message);
		}
		this->isNull = false;
		if (nullValue >= 0 && nullValue <= 127) {
			this->isNull = true;
			// we don't need the reason why it is null so we'll write byte 0
			/* A fixed-length data type that allows null values
			 will always consume one more byte than the datatype requires,
			 regardless of whether the actual value is null or non-null.
			 For example, an int8 will require 2 bytes and an int64
			 will require 9 bytes. (In the figure, see bytes 2-4 or 17-19.)
			 */
		} else if (nullValue == -1) {
			this->isNull = false;
		} else {

			/* if nullValue != -1: it means that there was another unexpected value
			 different from [-1,127] */
			std::string message(
					"For null indicator we expected value in the range but akegot: ");
			message += nullValue;
			message += " (read function null call in scidbAttribute.cpp).";
			throw DataMigratorException(message);
		}
	}
	if (this->value == NULL) {
		this->value = new T;
	}
	size_t elements_read = fread(this->value, this->bytesNumber, 1, this->fp);
	std::cout << "elements_read: " << elements_read << std::endl;
	std::cout << "bytes number in the attribute: " << this->bytesNumber
			<< std::endl;
	std::cout << "value: " << *(this->value) << std::endl;
	if (elements_read != 1) {
		std::string message(
				"No more data in the input file while reading data from "
						"the binary scidb file.");
		throw DataMigratorException(message);
	}
	return this; // everything is correct: success
}

template<class T>
void SciDBAttribute<T>::write(Attribute * attr) {
	uint32_t bytesNumber = attr->getBytesNumber();
//	printf("attr is null: %d\n", attr->getIsNull());
	if (attr->getIsNullable()) {
		if (attr->getIsNull()) {
			// we don't know the reason why it is null so we'll write byte 0
			char nullReason = 0;
			size_t numberOfElementsWritten = fwrite(&nullReason, 1, 1,
					this->fp);
			if (numberOfElementsWritten != 1) {
				std::string message("Could not write to the file.");
				throw DataMigratorException(message);
			}
			/* check if we can fill the size of the attribute with zeros */
			assert(Attribute::nullValuesSize >= bytesNumber);
			fwrite(Attribute::nullValues, bytesNumber, 1, this->fp);
			return; /* This has to be the end of the writting to the binary scidb. */
		} else {
			char notNull = -1;
			fwrite(&notNull, 1, 1, this->fp);
		}
	}
	/* Copy only the pointer. */
	T* value = static_cast<T*>(attr->getValue());
//	printf("value of the int: %d\n", *value);
	fwrite(value, bytesNumber, 1, this->fp);
}

/* implementation of the template specialization can be found in
 * scidbAttribute.cpp file */

template<>
Attribute * SciDBAttribute<char>::read();

template<>
void SciDBAttribute<char>::write(Attribute * attr);

template<>
Attribute * SciDBAttribute<bool>::read();

template<>
void SciDBAttribute<bool>::write(Attribute * attr);

#endif // SCIDB_ATTRIBUTE_H

