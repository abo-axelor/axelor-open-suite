/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2017 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.apps.bank.payment.service.invoice.payment;

import com.axelor.apps.account.db.InvoicePayment;
import com.axelor.apps.account.db.PaymentMode;
import com.axelor.apps.account.db.repo.InvoicePaymentRepository;
import com.axelor.apps.account.db.repo.PaymentModeRepository;
import com.axelor.apps.account.service.ReconcileService;
import com.axelor.apps.account.service.config.AccountConfigService;
import com.axelor.apps.account.service.move.MoveCancelService;
import com.axelor.apps.account.service.move.MoveLineService;
import com.axelor.apps.account.service.move.MoveService;
import com.axelor.apps.account.service.payment.PaymentModeService;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentToolService;
import com.axelor.apps.account.service.payment.invoice.payment.InvoicePaymentValidateServiceImpl;
import com.axelor.apps.bank.payment.db.BankOrder;
import com.axelor.apps.bank.payment.service.bankorder.BankOrderCreateService;
import com.axelor.apps.bank.payment.service.bankorder.BankOrderService;
import com.axelor.apps.base.db.Company;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

public class InvoicePaymentValidateServiceBankPayImpl  extends  InvoicePaymentValidateServiceImpl  {
	
	protected BankOrderCreateService bankOrderCreateService;
	protected BankOrderService bankOrderService;

	
	@Inject
	public InvoicePaymentValidateServiceBankPayImpl(PaymentModeService paymentModeService, MoveService moveService, MoveLineService moveLineService, 
			AccountConfigService accountConfigService, InvoicePaymentRepository invoicePaymentRepository, MoveCancelService moveCancelService, 
			ReconcileService reconcileService, BankOrderCreateService bankOrderCreateService,  
			BankOrderService bankOrderService, InvoicePaymentToolService invoicePaymentToolService)  {
		
		super( paymentModeService,  moveService,  moveLineService, accountConfigService,  invoicePaymentRepository,  moveCancelService, reconcileService,  invoicePaymentToolService);

		this.bankOrderCreateService = bankOrderCreateService;
		this.bankOrderService = bankOrderService;
		
	}
	
	
	
	/**
	 * Method to validate an invoice Payment
	 * 
	 * Create the eventual move (depending general configuration) and reconcile it with the invoice move
	 * Compute the amount paid on invoice
	 * Change the status to validated
	 * 
	 * @param invoicePayment
	 * 			An invoice payment
	 * 
	 * @throws AxelorException
	 * 		
	 */
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void validate(InvoicePayment invoicePayment) throws AxelorException  {
		
		if(invoicePayment.getStatusSelect() != InvoicePaymentRepository.STATUS_DRAFT)  {  return;  }
		
		PaymentMode paymentMode = invoicePayment.getPaymentMode();
		int typeSelect = paymentMode.getTypeSelect();
		int inOutSelect = paymentMode.getInOutSelect();
		
		if( (typeSelect == PaymentModeRepository.TYPE_DD || typeSelect == PaymentModeRepository.TYPE_TRANSFER) && inOutSelect == PaymentModeRepository.OUT){
			invoicePayment.setStatusSelect(InvoicePaymentRepository.STATUS_PENDING);
		}else{
			invoicePayment.setStatusSelect(InvoicePaymentRepository.STATUS_VALIDATED);
		}
		
		//TODO assign an automatic reference
		
		Company company = invoicePayment.getInvoice().getCompany();
				
		if(accountConfigService.getAccountConfig(company).getGenerateMoveForInvoicePayment() && !paymentMode.getGenerateBankOrder())  {
			this.createMoveForInvoicePayment(invoicePayment);
		}
		if(paymentMode.getGenerateBankOrder())  {
			this.createBankOrder(invoicePayment);
		}
		
		invoicePaymentToolService.updateAmountPaid(invoicePayment.getInvoice());
		invoicePaymentRepository.save(invoicePayment);

	}
	
	
	/**
	 * Method to create a bank order for an invoice Payment
	 * 
	 * 
	 * @param invoicePayment
	 * 			An invoice payment
	 * 
	 * @throws AxelorException
	 * 		
	 */
	@Transactional(rollbackOn = {AxelorException.class, Exception.class})
	public void createBankOrder(InvoicePayment invoicePayment) throws AxelorException  {
		
		BankOrder bankOrder = bankOrderCreateService.createBankOrder(invoicePayment);
		
		bankOrderService.confirm(bankOrder);
		
		invoicePayment.setBankOrder(bankOrder);
		
		invoicePaymentRepository.save(invoicePayment);
		
	}
	
	
	
	
}
